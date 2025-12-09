# PulseTrack Patient System

High-performance patient management microservices backend powered by distributed services , real time kafka events,   
gRPC communication, and Production Ready APIs.

# Overview:
This is a microservices-based patient management system designed to perform CRUD Operations on Patient Entity, send account creation notification to billing service, emits Kafka Events to analytics services, with production grade security via spring security and utilizes highly proficient api-gateway for authentication and routes management.
This project not only has development phase completed, but also the deployment phase with it.
It uses AWS CDK library in java to manage and create Infrastructure as Code (IoC).
In end , it is deployed to LocalStack Pro which simulates Real AWS Services
# Tech Stack:
- Spring Boot 4
- Spring 7
- Kafka Kraft
- gRPC
- Proto format
- Spring Data JPA
- Spring Cloud Gateway
- Localstack Pro
- AWS-CDK
- Jakarta Validation
- Postgres DB
- JWT
- Docker
- Dockerfile
- Swagger OpenAPI standard Api documentation.
- Using JavaDoc on My Ioc Code in infrastructure Folder


# Project Description:
PulseTrack Patient Management System showcases a complete production-ready microservices architecture designed for healthcare Operations.
It features:
- Secure Authentication.
- Patient Crud Capabilities.
- Distributed Billing and analytics workflow.
- Event driver communication via  Kafka and gRPC.

Infrastructure is managed through AWS CDK and deployed to LocalStack Pro, demonstrating the full lifecycle of building, containerizing and orchestrating distributed backend systems.

# My Aim in making this Project:
Okay, now the question much needed to answer is What was the reason of working on this project for the past whole month?

Well, the reason being is that i wanted to challenge myself, and along with it improve my backend engineering skills. Learn how production grade systems are made. What makes them so reliable?
How each of the step is done from creating and writing a single service to managing all the services running separately inside docker network as docker containers communicating with each other, while also being maintainable and scalable.

This Project improved my learning so much, i was able to learn alot through this.

My Overall understanding of systems improved from Basic Level CRUD to High Level Architectural thinking and keeping this in mind every step of the way "How to implement Best Practices".

I faced many issues during this project. Sometimes, for no reason the api is not working.
Some place i am missing a semicolon ＞﹏＜
Some times, authentication fails.
But , i prevailed through all of this.
I also kept my notes, made several notes using AI to keep track of what issues i encounterd, how i solved them , and what i got to learn from them.

To Sum up, this project greatly improved my skills from CRUD to High Level Development , Deployment and Orchestration using IoC (Infra as Code).


# Project Structure:

## 1. High Level Abstraction:

### Core Microservices and Their Responsibilities:

| Serial No | Service               | Responsibility                                                                                                                                                                               |
| :-------: | :-------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
|     1     | **Patient Service**   | Basic CRUD Operations in patient Service that go into the database, On Creation of new Patient, Data gets sent to billing service via **gRPC** and to analytis service via **Kafka events**. |
|     2     | **Billing Service**   | Receives data using **gRPC** in proto format from patient service, and performs insertion in the database.                                                                                   |
|     3     | **Auth Service**      | In this Architecture, its main responsibility is to **Create new User account or admin account** or perform **Login to generate the JWT Token**.                                             |
|     4     | **Analytics Service** | Receives the **Kafka Event** from patient service and Stores inside the Database for further processing.                                                                                     |
|     5     | **Api Gateway**       | A very important part of backend which manages **all routes**, and also on every request it performs **JWT validation** to authenticate.                                                     |

### Other Important Components:
Along with main services, Other important components are:
- Kafka Confluent Docker Container for patient service and analytics service
- Postgres Database containers for services
- Http Requests Folder
- LocalStack Pro Containers with their ECS fargate Service containers in deployment Phase.


## Detailed View:


```mermaid
%%{init: {'theme':'default'}}%%
flowchart TB
  root(["Patient-Production-Backend"])

  %% ---------- API + gRPC Requests ----------
  subgraph apiReqs["API-Requests"]
    apiAuth["AuthRequests
- CreateNewAccount.http
- LoginAccount.http"]

    apiPatient["PatientRequests
- CreatePatient.http
- GetAllPatients.http
- UpdatePatient.http"]
  end

  subgraph grpcReqs["GRPC-Requests"]
    grpcBilling["billing-service
- create-billing-account.http"]
  end

  %% ---------- Analytics Service ----------
  subgraph analytics["analytics-services"]
    an_src["src/main/java/com.ps.analyticsservices"]
    an_repo["repository
- PatientKafkaRepository"]
    an_service["KafkaConsumerService"]
    an_model["PatientEventDB"]
    an_proto["proto
- patient_event.proto"]
    an_res["resources
- application.properties
- static/
- templates/"]

    an_src --> an_repo
    an_src --> an_service
    an_src --> an_model
  end

  %% ---------- Auth Service ----------
  subgraph auth["auth-service"]
    au_src["src/main/java/com.ps.authservice"]
    au_config["config
- SecurityConfig"]
    au_controller["controller
- AuthController"]
    au_dto["dto
- LoginRequestDTO
- LoginResponseDTO
- SignUpDTO"]
    au_exception["exception
- GlobalExceptionHandler
- UserExistsAlreadyException"]
    au_model["model
- User"]
    au_repo["repository
- UserRepository"]
    au_service["service
- AuthService
- CustomUserDetailsService
- JwtService"]
    au_res["resources
- application.properties"]

    au_src --> au_config
    au_src --> au_controller
    au_src --> au_dto
    au_src --> au_exception
    au_src --> au_model
    au_src --> au_repo
    au_src --> au_service
  end

  %% ---------- Billing Service ----------
  subgraph billing["billing-service"]
    bi_src["src/main/java/com.ps.billingservice"]
    bi_grpc["grpc
- BillingGrpcService"]
    bi_app["BillingServiceApplication"]
    bi_proto["proto
- billing_service.proto"]
    bi_res["resources
- application.properties
- banner.txt
- static/
- templates/"]

    bi_src --> bi_grpc
    bi_src --> bi_app
  end

  %% ---------- Patient Service ----------
  subgraph patient["patient-service"]
    ps_src["src/main/java/com.ps.patientservice"]
    ps_controller["controller
- PatientController"]
    ps_dto["dto
- PatientCreateDTO
- PatientDTO
- PatientUpdateGroup"]
    ps_exception["exception
- EmailAlreadyExistsException
- PatientNotFoundException
- GlobalExceptionHandler"]
    ps_grpc["grpc
- BillingServiceGrpcClient"]
    ps_kafka["kafka
- KafkaProducer"]
    ps_mapper["mapper
- PatientMapper"]
    ps_model["model
- Patient"]
    ps_repo["repository
- PatientRepository"]
    ps_service["service
- PatientService"]
    ps_proto["proto
- billing_service.proto
- patient_event.proto"]
    ps_res["resources
- application.properties
- static/
- templates/"]

    ps_src --> ps_controller
    ps_src --> ps_dto
    ps_src --> ps_exception
    ps_src --> ps_grpc
    ps_src --> ps_kafka
    ps_src --> ps_mapper
    ps_src --> ps_model
    ps_src --> ps_repo
    ps_src --> ps_service
  end

  %% ---------- API Gateway ----------
  subgraph gateway["api-gateway"]
    gw_src["src/main/java/..."]
    gw_res["resources
- application.properties"]
  end

  %% ---------- Infrastructure / IaC ----------
  subgraph infra["infrastructure"]
    in_cdk["cdk.out
- localstack.template.json
- manifest.json
- tree.json"]
    in_src["src/main/java/com.ps.stack
- LocalStack"]
    in_test["test/java/com.ps.test
- PatientLoadScript
- Main"]
    in_script["localstack-deploy.sh"]
  end

  %% ---------- Root Links ----------
  root --> apiReqs
  root --> grpcReqs
  root --> analytics
  root --> auth
  root --> billing
  root --> patient
  root --> gateway
  root --> infra
```
