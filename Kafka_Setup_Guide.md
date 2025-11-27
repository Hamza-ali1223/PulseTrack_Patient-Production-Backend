

# Kafka KRaft + Kafka UI – Docker Setup Notes

These notes describe how to run:

* A **single-node Kafka broker in KRaft mode** (no ZooKeeper)
* A **Kafka UI dashboard** that connects from inside Docker

and at the same time allow **your host (IntelliJ, CLI, etc.)** to talk to Kafka.

---

## 1. High-Level Overview

### 1.1. What We’re Running

* **Kafka in KRaft mode (single node)**

    * One container: `kafka-kraft`
    * Plays **both** roles:

        * **Broker** (handles topics, producers, consumers)
        * **Controller** (stores metadata using Raft, replaces ZooKeeper)

* **Kafka UI**

    * One container: `kafka-ui`
    * Connects to Kafka through Docker network

### 1.2. Access Model (“Two-Door” Pattern)

We expose **two different listeners**:

| Listener   | Port  | Who uses it                           | Advertised as       |
| ---------- | ----- | ------------------------------------- | ------------------- |
| EXTERNAL   | 9092  | Your **host machine** (IntelliJ, CLI) | `localhost:9092`    |
| INTERNAL   | 29092 | **Other containers** (Kafka UI, apps) | `kafka-kraft:29092` |
| CONTROLLER | 29093 | Internal controller quorum            | `kafka-kraft:29093` |

* Host apps: `bootstrap.servers=localhost:9092`
* Docker apps: `bootstrap.servers=kafka-kraft:29092`

---

## 2. Docker Network

Both containers must be on the same Docker network so they can resolve each other by name.

```bash
docker network create patient-net
```

(You can also reuse `bridge`, but then you must ensure both containers attach to it.)

---

## 3. Kafka KRaft Container (`kafka-kraft`)

### 3.1. Image

* **Image**: `confluentinc/cp-kafka:latest` (or a pinned version)

### 3.2. Core Environment Variables

These are the important env vars and what they do:

| Variable                                         | Example Value                                                                 | Purpose                                                        |
| ------------------------------------------------ | ----------------------------------------------------------------------------- | -------------------------------------------------------------- |
| `KAFKA_NODE_ID`                                  | `1`                                                                           | ID of this broker in KRaft cluster.                            |
| `KAFKA_PROCESS_ROLES`                            | `broker,controller`                                                           | Tells Kafka this node is both broker and controller.           |
| `KAFKA_CONTROLLER_QUORUM_VOTERS`                 | `1@kafka-kraft:29093`                                                         | KRaft quorum definition. Use container name + controller port. |
| `KAFKA_LISTENERS`                                | `EXTERNAL://0.0.0.0:9092,INTERNAL://0.0.0.0:29092,CONTROLLER://0.0.0.0:29093` | Opens ports inside the container.                              |
| `KAFKA_ADVERTISED_LISTENERS`                     | `EXTERNAL://localhost:9092,INTERNAL://kafka-kraft:29092`                      | Tells clients **which host/port to use** for each listener.    |
| `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP`           | `CONTROLLER:PLAINTEXT,EXTERNAL:PLAINTEXT,INTERNAL:PLAINTEXT`                  | Map logical names to PLAINTEXT.                                |
| `KAFKA_CONTROLLER_LISTENER_NAMES`                | `CONTROLLER`                                                                  | Controller traffic uses the `CONTROLLER` listener.             |
| `KAFKA_INTER_BROKER_LISTENER_NAME`               | `INTERNAL`                                                                    | Brokers talk to each other on the `INTERNAL` listener.         |
| `KAFKA_LOG_DIRS`                                 | `/var/lib/kafka/data`                                                         | Where data is stored (volume-mapped).                          |
| `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR`         | `1`                                                                           | Single-node cluster.                                           |
| `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR` | `1`                                                                           | Single-node cluster.                                           |
| `KAFKA_TRANSACTION_STATE_LOG_MIN_ISR`            | `1`                                                                           | Single-node cluster.                                           |
| `KAFKA_AUTO_CREATE_TOPICS_ENABLE`                | `true` (optional)                                                             | Automatically create topics on first use.                      |

> **Key point:**
> `EXTERNAL` must advertise `localhost` so **your host** can connect.
> `INTERNAL` must advertise `kafka-kraft` so **other containers** can connect.

### 3.3. Persistence (Volume)

Mount a local folder to keep Kafka logs between restarts:

* Host path → `/var/lib/kafka/data` (inside container)

Example host folder: `./kafka-data`

This prevents new Cluster IDs / “fresh cluster” behavior on each restart.

### 3.4. Example `docker-compose` Service for Kafka

```yaml
version: "3.8"

services:
  kafka-kraft:
    image: confluentinc/cp-kafka:latest
    container_name: kafka-kraft
    networks:
      - patient-net
    ports:
      - "9092:9092"    # external listener for host
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: "broker,controller"
      KAFKA_LISTENERS: "EXTERNAL://0.0.0.0:9092,INTERNAL://0.0.0.0:29092,CONTROLLER://0.0.0.0:29093"
      KAFKA_ADVERTISED_LISTENERS: "EXTERNAL://localhost:9092,INTERNAL://kafka-kraft:29092"
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: "CONTROLLER:PLAINTEXT,EXTERNAL:PLAINTEXT,INTERNAL:PLAINTEXT"
      KAFKA_CONTROLLER_LISTENER_NAMES: "CONTROLLER"
      KAFKA_CONTROLLER_QUORUM_VOTERS: "1@kafka-kraft:29093"
      KAFKA_INTER_BROKER_LISTENER_NAME: "INTERNAL"
      KAFKA_LOG_DIRS: "/var/lib/kafka/data"
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    volumes:
      - ./kafka-data:/var/lib/kafka/data

networks:
  patient-net:
    external: true
```

If you put Kafka and Kafka-UI in the same compose file, move the `networks` section accordingly.

---

## 4. Kafka UI Container (`kafka-ui`)

### 4.1. Image

* **Image**: `provectuslabs/kafka-ui:latest`

### 4.2. Core Environment Variables

| Variable                            | Example Value       | Purpose                                                    |
| ----------------------------------- | ------------------- | ---------------------------------------------------------- |
| `KAFKA_CLUSTERS_0_NAME`             | `Local-KRaft`       | Display name in UI.                                        |
| `KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS` | `kafka-kraft:29092` | Bootstrap servers for the UI (uses the INTERNAL listener). |

Because we’re in KRaft mode, we **don’t** need ZooKeeper env vars.

### 4.3. Example `docker-compose` Service for Kafka UI

```yaml
  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: kafka-ui
    networks:
      - patient-net
    depends_on:
      - kafka-kraft
    ports:
      - "8080:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: "Local-KRaft"
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: "kafka-kraft:29092"
```

Then you can open:

* **Kafka UI** at: `http://localhost:8080`

---

## 5. Step-by-Step Run Instructions

1. **Create the network** (if not already):

   ```bash
   docker network create patient-net
   ```

2. **Start Kafka** (either via `docker-compose up kafka-kraft` or `docker run` with same env/env vars & network).

    * Wait until logs show something like:

        * `Kafka Server started`
        * `Broker started`
        * Controller node becoming active / unfenced

3. **Start Kafka UI**:

   ```bash
   docker-compose up kafka-ui
   ```

4. **Verify from Kafka UI**:

    * Visit `http://localhost:8080`
    * You should see the `Local-KRaft` cluster and be able to browse topics.

5. **Verify from host (IntelliJ / CLI)**:

    * Use `bootstrap.servers=localhost:9092` in Java/Spring apps.
    * Or use console tools (if you have Kafka CLI installed) with `--bootstrap-server localhost:9092`.

6. **Verify from another container (e.g., microservice)**:

    * Inside `patient-net`, use `bootstrap.servers=kafka-kraft:29092`.

---

## 6. Common Pitfalls (Things We Broke Before Fixing)

### 6.1. Wrong `ADVERTISED_LISTENERS` for Internal Traffic

**Mistake:**

```text
INTERNAL listener advertised as localhost:29092
```

**Symptom:**

* Kafka UI (inside container) tries to connect to `localhost:29092` **inside its own container**, not the Kafka container.
* Result: connection refused / “broker not available”.

**Fix:**

* Internal advertised listener must be:

  ```text
  INTERNAL://kafka-kraft:29092
  ```

  where `kafka-kraft` is the **container name** on the shared Docker network.

---

### 6.2. Wrong `ADVERTISED_LISTENERS` for External Traffic

**Mistake:**

```text
EXTERNAL listener advertised as kafka-kraft:9092
```

**Symptom:**

* Your host machine (Windows/Mac/Linux) cannot resolve `kafka-kraft`.
* IntelliJ / CLI fails with `UnknownHostException` or similar.

**Fix:**

* External advertised listener must be:

  ```text
  EXTERNAL://localhost:9092
  ```
* That’s what your host resolves.

---

### 6.3. No Shared Docker Network

**Mistake:**

* Kafka and Kafka UI are on different networks (or one is on default bridge and the other isn’t).

**Symptom:**

* Kafka UI cannot resolve `kafka-kraft` hostname.
* You see network errors, even if `BOOTSTRAPSERVERS` looks correct.

**Fix:**

* Attach both containers to the same network (e.g., `patient-net`).
* Confirm with:

  ```bash
  docker inspect kafka-kraft
  docker inspect kafka-ui
  ```

---

### 6.4. No Volume = “Fresh Cluster Every Time”

**Mistake:**

* Running Kafka without a volume on `/var/lib/kafka/data`.

**Symptom:**

* Topics vanish after container recreation.
* Logs show new cluster ID each time, which can cause confusion.

**Fix:**

* Mount a host directory to `/var/lib/kafka/data`:

  ```yaml
  volumes:
    - ./kafka-data:/var/lib/kafka/data
  ```

---

## 7. How Microservices Should Connect

* **From Host (e.g., running Spring Boot from IntelliJ):**

  ```properties
  spring.kafka.bootstrap-servers=localhost:9092
  ```

* **From Containers on `patient-net`:**

  ```properties
  spring.kafka.bootstrap-servers=kafka-kraft:29092
  ```

This matches our **two-door listener pattern** and avoids hostname/localhost confusion.

---

