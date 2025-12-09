package com.ps.stack;

import com.amazonaws.services.kafka.model.KafkaVersion;
import com.amazonaws.services.kafka.model.KafkaVersionStatus;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.CfnHealthCheck;
import software.constructs.IConstruct;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
So, this is a class code that implements our Infrastructure for my project here, The name is LocalStack because of the reason that i am using localstack to simulate the usage of aws, as the cdk.out of this code will be used in cloud formation step


This Class "LocalStack" extends Stack which is a construct in AWS CDk ( Cloud Development Kit)
this stack itself goes into our App Base Construct and that is where we do app.synth() to generate our output
You can visit official AWS CDK docs to understand about Constructs, their levels and their model & paradigm.

Everything we add here in this stack which includes our MSK, RDS, VPC, Health Checks and etc etc will go into our
this single Stack

 */
public class LocalStack extends Stack {

    //Made VPC variable global as we will use it frequently and our resources will be in this vpc
    private final Vpc vpc;

    //Made Ecs Cluster Variable also Global
    private final Cluster ecsCluster;


    private final boolean useMsk;
    /**
    Below is our LocalStack Constructor that defines app scope, id , props and passes to it's super class
    Further it creates Vpc using our CreateVPC() method that returns an instance of vpc

    Moving forward, we make Following items:
    1. Auth Service Db Instance with id and db name using createDatabase() method
    2. Patient Service Db Instance with id and db name using createDatabase() method
    3. Two Health Checks that are made using L1 Constructs of HealthCheck which are CfnHealthCheck that will verify
        our databases are ready to connect or not
     */
    public LocalStack(final App scope, final String id, final StackProps props) {
        super(scope, id, props);
        this.vpc = createVpc();

        this.useMsk = Boolean.parseBoolean(
                System.getenv().getOrDefault("USE_MSK", "false")
        );
        DatabaseInstance authServiceDb = createDatabase("AuthServiceDB", "authdb");
        DatabaseInstance patientServiceDb = createDatabase("PatientServiceDB", "patientdb");
        DatabaseInstance analyticsServiceDb= createDatabase("AnalyticsServiceDB","analyticsdb");

        CfnHealthCheck authHealthCheck = createDbHealthCheck(authServiceDb, "AuthServiceDBHealthCheck");
        CfnHealthCheck patientHealthCheck = createDbHealthCheck(patientServiceDb, "PatientServiceDBHealthCheck");
        CfnCluster mskCluster = null;

        if(useMsk)
        {
           mskCluster = createMskCluster();

        }




        this.ecsCluster = createEcsCluster();

        FargateService authService = createFargateService("AuthService",
                "auth-service",List.of(4005),authServiceDb,Map.of("JWT_SECRET","2c46a678a7da9f4049225be3970c3d367370617004b49cb70195422aa7eb4131"));

        authService.getNode().addDependency(authHealthCheck);
        authService.getNode().addDependency(authServiceDb);


        FargateService billingService = createFargateService("BillingService","billing-service",List.of(9001),null,null);

        FargateService analyticsService = createFargateService("AnalyticsService","analytics-service",List.of(4002),analyticsServiceDb,null);


        if (useMsk && mskCluster != null) {
            analyticsService.getNode().addDependency(mskCluster);
        }
        analyticsService.getNode().addDependency(analyticsServiceDb);
        FargateService patientService = createFargateService("PatientService",
                "patient-service",
                List.of(4000),
                patientServiceDb
        ,Map.of("BILLING_SERVICE_ADDRESS","host.docker.internal"
        ,"BILLING-SERVICE_GRPC_PORT","9001"));

        patientService.getNode().addDependency(patientServiceDb);

        if (useMsk && mskCluster != null) {
           patientService.getNode().addDependency(mskCluster);
        }
        patientService.getNode().addDependency(billingService);
        patientService.getNode().addDependency(patientHealthCheck);

        //Make ApiGateway
        createApiGatewayService();


    }


    //Method that returns VPC instance with configurations
    private Vpc createVpc() {
        return Vpc.Builder.create(this, "PatientManagementVPC")
                .vpcName("PatientManagmentVPC")
                .maxAzs(2)
                .build();
    }

    //Method that returns Db Instance wit configurations
    private DatabaseInstance createDatabase(String id, String dbName) {
        return DatabaseInstance.Builder
                .create(this, id)
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_17_2)
                                .build()))
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
                .allocatedStorage(20)
                .credentials(Credentials.fromGeneratedSecret("root"))
                .databaseName(dbName)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
    }


    //Method that returns L1 Construct CfnHealthChecks of db's
    private CfnHealthCheck createDbHealthCheck(DatabaseInstance db, String id)
    {
        return CfnHealthCheck.Builder
                .create(this,id)
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP")
                        .port(Token.asNumber(db.getDbInstanceEndpointPort()))
                        .ipAddress(db.getDbInstanceEndpointAddress())
                        .requestInterval(30)
                        .failureThreshold(3)
                .build())
                .build();
    }


    //Method that returns a MSk Cluster for kafka usage
    private CfnCluster createMskCluster ()
    {
        return CfnCluster.Builder
                .create(this, "MskCluster")
                .kafkaVersion("3.8.x.kraft")
                .clusterName("MskCluster")
                .numberOfBrokerNodes(2)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                        .instanceType("kafka.m5.xlarge")
                        .clientSubnets(vpc.getPrivateSubnets().stream().map(ISubnet::getSubnetId).toList())
                        .brokerAzDistribution("DEFAULT")
                        .build()
                )
                .build();
    }


    //Method to Add our Ecs Cluster via returning it's instance
    private Cluster createEcsCluster()
    {
            return Cluster.Builder
                    .create(this, "PatientManagementCluster")
                    .vpc(vpc)
                    .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                            .name("patient-management.local")
                            .build())
                    .build();
    }

    /**
     This is our FargetService instance method that returns an instance and it makes our ECS task,
     it takes Task id, it'simage name , list of ports, if that task of ECS uses a db, and additional Params
     <br>
     - It Also first does task Defintion, then we make ContainerDefinitionOptions , then by checking if db and envvars is not empty
     then we add their properties , in last returning our instance of fargate Service with necessary configurations
     @Returns FargateService ECS Task

     */
    private FargateService createFargateService(String id , String imageName, List<Integer> ports, DatabaseInstance db, Map<String, String> additionalEnvVars)
    {
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder
                .create(this, id + "Task")
                .cpu(256)
                .memoryLimitMiB(512)
                .build();

        ContainerDefinitionOptions.Builder containerDefinitionOptions = ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry(imageName))
                .portMappings(ports.stream().map(port -> PortMapping.builder()
                        .containerPort(port)
                        .hostPort(port)
                        .protocol(Protocol.TCP)
                        .build()).toList())
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder
                                        .create(this, id + "LogGroup")
                                        .logGroupName("/ecs/"+imageName)
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build())
                                .streamPrefix(imageName)
                        .build()));
        
        
        Map<String, String> envVars = new ConcurrentHashMap<>();
        envVars.put("SPRING_KAFKA_BOOTSTRAP_SERVERS","host.docker.internal:9092");
        
        if(additionalEnvVars!=null)
        {
            envVars.putAll(additionalEnvVars);
        }
        
        if(db!= null)
        {
            envVars.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://host.docker.internal:5432/patient-db");
            envVars.put("SPRING_DATASOURCE_USERNAME", "root");
            envVars.put("SPRING_DATASOURCE_PASSWORD", "root");
            envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO","update");
            envVars.put("SPRING_SQL_INIT_MODE","always");
            envVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT","60000");
            envVars.put("SPRING_JPA_SHOW_SQL","true");
        }

        if(envVars!=null)
        {
            containerDefinitionOptions.environment(envVars);
        }

        ContainerDefinitionOptions buildCompletedOptions = containerDefinitionOptions.build();

        taskDefinition.addContainer(imageName + "Container", buildCompletedOptions);


        return FargateService.Builder
                .create(this,id)
                .cluster(ecsCluster)
                .taskDefinition(taskDefinition)
                .assignPublicIp(false)
                .serviceName(imageName)
                .build();

    }

    private ApplicationLoadBalancedFargateService  createApiGatewayService()
    {
        FargateTaskDefinition fargateTaskDefinition = FargateTaskDefinition.Builder
                .create(this, "APIGatewayTaskDefinition")
                .cpu(256)
                .memoryLimitMiB(512)
                .build();

        ContainerDefinitionOptions.Builder containerDefinitionOptions = ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("api-gateway"))
                .portMappings(List.of(4004).stream().map(port -> PortMapping.builder()
                        .containerPort(port)
                        .hostPort(port)
                        .protocol(Protocol.TCP)
                        .build()).toList())
                .environment(Map.of("SPRING_PROFILES_ACTIVE","prod"
                ,"AUTH_SERVICE_URL","http://host.docker.internal:4005"))
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(LogGroup.Builder
                                .create(this,  "ApiGatwayLogGroup")
                                .logGroupName("/ecs/api-gateway")
                                .removalPolicy(RemovalPolicy.DESTROY)
                                .retention(RetentionDays.ONE_DAY)
                                .build())
                        .streamPrefix("api-gateway")
                        .build()));

        ContainerDefinitionOptions buildComplete = containerDefinitionOptions.build();

        fargateTaskDefinition.addContainer("APIGatewayTaskDefinition", buildComplete);

        ApplicationLoadBalancedFargateService apiGateway = ApplicationLoadBalancedFargateService
                .Builder
                .create(this,"APIGatewayService")
                .cluster(this.ecsCluster)
                .taskDefinition(fargateTaskDefinition)
                .assignPublicIp(true)
                .desiredCount(1)
                .healthCheckGracePeriod(Duration.seconds(60))
                .build();

        return apiGateway;

    }

    /**
    This Main Method has our App instance configured with output directory and also we have our StackProps
    that uses special Synthesizer, learn more about it learn on.

     Few Outputs Line for additional Information
     */
    public static void main(String[] args) {
        App app = new App(AppProps.builder().outdir("./cdk.out").build());

        StackProps props = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer())
                .build();



        new LocalStack(app, "localstack", props);
        app.synth();

        System.out.println("App Synthesizing in Progress");
        for(int i = 1 ; i <= app.getNode().getChildren().getFirst().getNode().getChildren().size(); i++)
        {
            System.out.println("Construct #"+i+" in our LocalStack: " + app.getNode().getChildren().getFirst().getNode().getChildren().get(i-1).toString());
        }
        System.err.println("App Scopes: "+app.getNode().getScopes());
        System.err.println("App Dependencies: "+app.getNode().getDependencies());

    }
}
