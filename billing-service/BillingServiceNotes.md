
# ✨ **Tip 01 – gRPC Fundamentals (Billing-Service Edition)**

|                                 |                                                                                                                                                                                        |   |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | - |
| 📌 **What**                     | **gRPC** = Google’s high-performance, binary **Remote Procedure Call** framework (2015). Uses **HTTP/2** + **Protocol Buffers** so microservices invoke each other like local methods. |   |
| 🛠 **How It Works**             | 1️⃣ Write a **`.proto`** contract → 2️⃣ **protoc** generates language stubs → 3️⃣ Server implements stub → 4️⃣ Clients call *method()* ↔ network hidden.                               |   |
| 🚀 **Why We Use It**            | • Tiny payloads (Protobuf) • Multiplexed streams (HTTP/2) • Bi-di streaming • Strong, contract-first typing • Auto-gen clients.                                                        |   |
| 🧭 **When to Use**              | Internal microservice calls, mobile backends, telemetry, any low-latency or streaming need.                                                                                            |   |
| ⚠️ **When Not**                 | Public browser APIs (needs gRPC-Web proxy) or when human-readable JSON & REST caching are must-haves.                                                                                  |   |
| 🔀 **Call Styles**              | Unary • Server-stream • Client-stream • Bi-Directional stream.                                                                                                                         |   |
| 🗺 **Typical Flow**             | Edge REST (👩‍💻) → **gRPC mesh** (🤖) → Kafka/events (📬) for async decoupling.                                                                                                       |   |
| 🛡 **Versioning Rule of Thumb** | **Never reuse field numbers.** Add fields (back-compatible), mark removed ones as `reserved`, bump major on breaking changes.                                                          |   |
| 🧪 **Quick-test Toolkit**       | `grpcurl` (CLI), `grpcui` (Web UI), Postman (gRPC beta), Wireshark (HTTP/2), **Buf** (lint & breaking-change guard).                                                                   |   |

> 📝 **Memory hook:** *“REST speaks human 📄, gRPC speaks service ⚡.”*



# ✨ **Tip 02 – Copy-Paste gRPC Starter Kit for Any Spring-Boot 3 (+ Java 17–21) Service**



```xml
<!-- 📄 pom.xml -->
<!-- 1️⃣ Align every gRPC module via BOM -->
<dependencyManagement>
<dependencies>
<dependency>
<groupId>io.grpc</groupId>
<artifactId>grpc-bom</artifactId>
<version>1.63.0</version>   <!-- 🔄 UPDATE on next gRPC release -->
<type>pom</type>
<scope>import</scope>
</dependency>
</dependencies>
</dependencyManagement>

<dependencies>
<!-- 2️⃣ Spring Boot gRPC integration -->
<dependency>
<groupId>net.devh</groupId>
<artifactId>grpc-spring-boot-starter</artifactId>
<version>3.1.0.RELEASE</version> <!-- matches Spring Boot 3.x -->
</dependency>

<!-- 3️⃣ Add only if you ALSO expose REST endpoints -->
<!-- <dependency>
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-web</artifactId>
</dependency> -->
</dependencies>

<build>
<extensions>
<!-- Detect OS for native protoc binary -->
<extension>
<groupId>kr.motd.maven</groupId>
<artifactId>os-maven-plugin</artifactId>
<version>1.7.0</version>
</extension>
</extensions>

<plugins>
<!-- 4️⃣ Protobuf & gRPC stub generator -->
<plugin>
<groupId>org.xolstice.maven.plugins</groupId>
<artifactId>protobuf-maven-plugin</artifactId>
<version>0.6.1</version>
<configuration>
<!-- Protobuf compiler -->
<protocArtifact>
com.google.protobuf:protoc:3.25.1:exe:${os.detected.classifier}
</protocArtifact>

<!-- gRPC Java codegen (MUST match BOM) -->
<pluginId>grpc-java</pluginId>
<pluginArtifact>
io.grpc:protoc-gen-grpc-java:1.63.0:exe:${os.detected.classifier}
</pluginArtifact>
</configuration>
<executions>
<execution>
<goals>
<goal>compile</goal>
<goal>compile-custom</goal>
</goals>
</execution>
</executions>
</plugin>
</plugins>
</build>
```

### 📂 **Standard Folder Layout**

```
billing-service/
├─ src/main/java/com/ps/billingservice/…      ← business & gRPC impl
├─ src/main/proto/billing.proto                ← contracts live here
└─ src/main/resources/application.yml          ← set grpc.server.port if 9090 clashes
```

### 🏃 **Bootstrap Steps**

1. **Write contract** in `billing.proto` (service + messages).
2. `mvn clean compile` → stubs land in `target/generated-sources`.
3. Implement server:

```java
@GrpcService
public class BillingImpl extends BillingServiceGrpc.BillingServiceImplBase { … }
```
4. Inject client in another service:

```java
@GrpcClient("billingService")
BillingServiceGrpc.BillingServiceBlockingStub stub;
```
5. Test with `grpcurl -plaintext localhost:9090 list`.

### 🔄 **Future-proof Checklist**

| ⏭ What to bump                        | Where                                                                                         | Why                                                                                       |
| ------------------------------------- | --------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| **grpc-bom version**                  | `<dependencyManagement>`                                                                      | Keeps all gRPC libs in lock-step.                                                         |
| **protoc** & **protoc-gen-grpc-java** | inside `protobuf-maven-plugin`                                                                | Must match BOM **or** you’ll hit `javax.annotation` vs `jakarta.annotation` issues again. |
| **grpc-spring-boot-starter**          | `<dependencies>`                                                                              | Ensure it supports the new BOM / Spring Boot release.                                     |
| **javax.annotation-api**              | Only add if generated code still imports `javax.annotation.Generated` under Java 17 or below. |                                                                                           |

### 🛠 **Common Tweaks**

| Need                  | Action                                                                             |
| --------------------- | ---------------------------------------------------------------------------------- |
| Pure gRPC (no Tomcat) | Remove `spring-boot-starter-web` *or* set `spring.main.web-application-type=none`. |
| TLS                   | `grpc.server.security.enabled=true` + cert/key paths in `application.yml`.         |
| Shared proto repo     | Publish contracts as `company-protos` JAR; import instead of duplicating files.    |
| CI lint & break check | Add **Buf**: `buf lint`, `buf breaking --against .git#branch=main`.                |

---


# ✨ **Tip 03 – Correctly Overriding gRPC Methods & Avoiding Stub Desync Issues**

When working with gRPC in a Java/Spring Boot microservice, the server-side implementation **must** override the exact method signature generated by `protoc-gen-grpc-java`. A mismatch—even just a capital letter—causes the RPC to appear “unimplemented,” even though your logic exists.

This tip explains **why this happens**, **how to recognize it**, and **how to permanently avoid it**.

---

## 🧩 **1. Understanding How gRPC Generates Server Methods**

Your `.proto` file defines the RPC:

```proto
rpc CreateBillingAccount (BillingRequest) returns (BillingResponse);
```

But when Protobuf generates Java code, it applies **camelCase rules**:

| Proto                  | Java                   |
| ---------------------- | ---------------------- |
| `CreateBillingAccount` | `createBillingAccount` |

So the **ONLY correct server-side override** is:

```java
@Override
public void createBillingAccount(BillingRequest req,
                                 StreamObserver<BillingResponse> resp) { … }
```

Java is case-sensitive →
`CreateBillingAccount` ≠ `createBillingAccount`

Even changing **1 character** breaks the override.

---

## 🚨 **2. The Surprise Problem You Encountered**

Your generated file contained an **extra abstract method**:

```java
public abstract void CreateBillingAccount(...);
```

This is *not* what modern protoc should generate, and it caused **two different method names** to exist:

* `createBillingAccount(...)` → used by gRPC dispatch system
* `CreateBillingAccount(...)` → leftover abstract method requiring override

### ⚡ Result:

* Your class correctly implemented `createBillingAccount`
* BUT Java saw that **CreateBillingAccount** was NOT implemented
* IntelliJ warned:

  ```
  Class must implement abstract method CreateBillingAccount(...)
  ```
* gRPC called the correct method (`createBillingAccount`)
* But compile-time failed because of the stray abstract method
* IntelliJ gRPC client showed:

  ```
  UNIMPLEMENTED: Method BillingService/CreateBillingAccount is unimplemented
  ```

This happened because your **generated file was stale/out-of-sync**, likely due to:

* An old generated stub still in `target/generated-sources`
* A previous IntelliJ “implement method” quick-fix that modified the stub
* A partial rebuild that did not clean the previous generation

Generated files should *never* be manually touched — they must be regenerated.

---

## 🛠️ **3. How to Fix (Permanent Clean Approach)**

Follow this checklist whenever proto changes or errors like “unimplemented” appear:

### **✔ Step 1 — Delete old generated sources**

Remove:

```
/target/generated-sources/protobuf/
```

This ensures no stale files remain.

### **✔ Step 2 — Regenerate stubs properly**

Run:

```bash
mvn clean compile
```

This forces protoc and the grpc-java plugin to regenerate the official stubs.

### **✔ Step 3 — Open the regenerated stub**

File:

```
target/generated-sources/protobuf/grpc-java/billing/BillingServiceGrpc.java
```

Find the exact method signature inside:

```java
public void createBillingAccount(...);
```

That is the **only** method you must override.

### **✔ Step 4 — Implement EXACTLY that method**

```java
@Override
public void createBillingAccount(BillingRequest request,
                                 StreamObserver<BillingResponse> responseObserver) {
    // your business logic
    responseObserver.onNext(response);
    responseObserver.onCompleted();
}
```

No capitalization changes.
No renaming.
No manual rewrites.

---

## 🧠 **4. How to Prevent This in Future Projects**

Use this checklist **every time you define a new RPC method:**

### **🟦 Before coding**

* Write RPC in `.proto`
* Run `mvn clean compile`
* Open the generated `ServiceGrpc.java`
* Copy the server method signature EXACTLY

### **🟩 During development**

* NEVER edit files in `target/generated-sources`
* NEVER use IntelliJ “implement missing method” inside generated files
* ALWAYS check for stale generated code when seeing "unimplemented"

### **🟥 When you see ANY of these errors:**

* *“Method X is unimplemented”*
* *“must either be declared abstract or implement abstract method”*
* *IntelliJ gRPC call times out with UNIMPLEMENTED*

Your first action must be:

```
mvn clean compile
```

Because the stubs and proto definitions are out of sync.

---

## 🎯 **Final Takeaway (Tip 03 Essence)**

> **gRPC server methods must match the EXACT Java signature generated from the proto.
> Any stale or mismatched generated code breaks overrides and results in UNIMPLEMENTED errors.
> Always regenerate stubs with `mvn clean compile` before implementing RPC logic.**

This rule will save you hours in every future microservice you build.

---
---

# 🌟 **Tip 04 – Building a gRPC Client in Spring Boot (Patient-Service → Billing-Service)**

This tip explains how to create a complete gRPC client inside **Patient-Service** to call **Billing-Service** over RPC. It covers every concept & line of code so that future-you can build new gRPC clients easily in any microservice.

---

## 🎯 **1. Learning Objective**

By the end of this tip, you will know:

* How to configure host/port dynamically using `@Value`
* How to create a `ManagedChannel` to any gRPC server
* What `ManagedChannelBuilder` does
* How `usePlaintext()` works
* What a *blocking stub* is
* How to create & send a gRPC request
* How Patient-Service used this client inside `savePatient()`
* How to reuse this exact pattern for any future microservice-to-microservice RPC call

---

# 🏗️ **2. The Final gRPC Client (Full Code)**

```java
@Service
public class BillingServiceGrpcClient
{
    private static final Logger log = LoggerFactory.getLogger(BillingServiceGrpcClient.class);
    private final BillingServiceGrpc.BillingServiceBlockingStub blockingStub;

    public BillingServiceGrpcClient(
            @Value("${billing.service.address:localhost}") String serverAddress,
            @Value("${billing.service.grpc.port:9001}") int serverPort)
    {
        log.info("Connecting to Billing Server GRPC server at {}:{}", serverAddress, serverPort);

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(serverAddress, serverPort)
                .usePlaintext()   // No TLS → perfect for local dev & Docker internal networking
                .build();

        blockingStub = BillingServiceGrpc.newBlockingStub(channel);
    }

    public BillingResponse createBillingAccount(String patientId, String name, String email)
    {
        BillingRequest request = BillingRequest.newBuilder()
                .setPatientId(patientId)
                .setName(name)
                .setEmail(email)
                .build();

        BillingResponse response = blockingStub.createBillingAccount(request);
        log.info("Created Billing Account from billing service via GRPC: {}", response);
        return response;
    }
}
```

---

# 📦 **3. Line-by-Line Explanation (What Each Piece Does)**

### **🔹 @Service**

Marks this class as a Spring bean, allowing other services (e.g., `PatientService`) to inject it.

---

### **🔹 @Value("${billing.service.address:localhost}")**

Spring expression meaning:

* Try to read `billing.service.address` from config
* If missing → default to `"localhost"`

Same for port:

```java
@Value("${billing.service.grpc.port:9001}")
```

This makes your client environment-friendly:

* **Local** → localhost
* **Docker** → service name (`billing-service`)
* **Kubernetes** → internal DNS (`billing-service.default.svc.cluster.local`)

You only change values in `application.properties`, not code.

---

### **🔹 ManagedChannel & ManagedChannelBuilder**

```java
ManagedChannel channel = ManagedChannelBuilder
        .forAddress(serverAddress, serverPort)
        .usePlaintext()
        .build();
```

A **ManagedChannel** is the gRPC client’s TCP/HTTP2 connection manager.

It handles:

* DNS resolution
* Load balancing
* Connection pooling
* Retries
* Serializing/deserializing messages

#### Why `.usePlaintext()`?

Because gRPC defaults to TLS.
For local dev & Docker internal networks → plaintext is faster and simpler.

Production would use `.useTransportSecurity()` with certificates.

---

### **🔹 The Blocking Stub**

```java
blockingStub = BillingServiceGrpc.newBlockingStub(channel);
```

The **blocking stub**:

* Is auto-generated from the `.proto` file
* Gives you strongly typed Java methods
* Each call blocks the thread until a response arrives
* Perfect for simple request-response RPCs

Example:

```java
BillingResponse response = blockingStub.createBillingAccount(request);
```

It *feels* like a normal Java method call — but under the hood:

1. Converts your request to Protobuf
2. Sends over HTTP/2
3. Waits for BillingService handler to process it
4. Parses Protobuf response
5. Returns it to your method

---

# 🔄 **4. How Patient-Service Used This Client in savePatient()**

Inside:

```java
public Patient savePatient(Patient patient)
```

You:

1. Saved the patient → generated a UUID
2. Called:

```java
billingServiceGrpcClient.createBillingAccount(
        saved.getId().toString(),
        saved.getName(),
        saved.getEmail()
);
```

3. Billing-Service responded with:

```
accountId: "...uuid..."
status: "Active Response"
```

This means your cross-service RPC workflow is now working perfectly.

---

# 🔧 **5. Reusing This Pattern for Any Future Microservice**

To create a new gRPC client (e.g., Appointment-Service, Inventory-Service, Email-Service):

1. Add the generated stubs (via proto)
2. Create a similar `GrpcClient` class
3. Inject host/port via `@Value`
4. Build a channel
5. Create a stub
6. Wrap request/response methods

This structure is reusable forever.

---

# 📘 **6. Final Summary (Mental Model for Future You)**

**What this class really does:**

* Reads host & port from configuration
* Opens an HTTP/2 gRPC channel
* Creates a blocking client proxy stub
* Sends BillingRequest messages
* Receives strongly typed BillingResponse messages
* Allows Patient-Service to communicate with Billing-Service directly
* Used inside `savePatient()` right after DB insertion

**This is the standard gRPC client pattern for Spring microservices.**

---
