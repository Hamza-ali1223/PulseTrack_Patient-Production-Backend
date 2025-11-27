# 📕 API Gateway & Spring Cloud Gateway – Cheat Sheet

> **Level 📈 Junior → Intermediate**

---

## 1️⃣ What is an API Gateway? 🚪

**Single entry point** that sits between clients and all your micro‑services.

| 💡 **Does**                      | 🚫 **Does NOT**      |
| -------------------------------- | -------------------- |
| ➡️ Routes requests               | ❌ Run business logic |
| 🛡️ Enforces auth & rate limits  | ❌ Talk to databases  |
| 🔄 Rewrites paths / headers      | ❌ Heavy CPU work     |
| 📊 Centralized metrics & logging |                      |

---

## 2️⃣ Why use one? 🤔

* Hide internal service topology 🕵️‍♂️
* Apply cross‑cutting concerns **once** (auth, CORS, limits) ♻️
* Support versioning & canary deployments 🎯
* Reduce client complexity 📉

---

## 3️⃣ Mental Model 🧠

```
Client 📱/🌐
    ↓ single TCP hop
API Gateway 🌐 (Spring Cloud Gateway)
    ↙︎          ↘︎
 patient‑svc   billing‑svc   …
```

`Route = Predicate (match) + Filters (mutate) + Target URI (forward)`

---

## 4️⃣ Spring Cloud Gateway (SCG) 🌀

* **Reactive‑only** → built on **Project Reactor + Netty** ⚡️
* Official successor to Zuul 1 🏆
* Configuration styles:

    * **Java DSL**
    * **`application.yml`**
* Extensible with custom filters ☕️

---

## 5️⃣ Building a Route 🏗️

```yaml
spring:
  cloud:
    gateway:
      routes:
      - id: patient-svc
        uri: http://patient-service:4000
        predicates:
          - Path=/patients/**
        filters:
          - StripPrefix=1
```

* **Predicate** `Path=/patients/**` → match incoming URL
* **Filter** `StripPrefix=1` → remove first segment
* **URI** → where to send the request inside Docker network 🐳

---

## 6️⃣ Common Predicates 🔍

| Predicate                    | Purpose            |
| ---------------------------- | ------------------ |
| `Path`                       | Match URL pattern  |
| `Method`                     | GET / POST / …     |
| `Header`                     | Match header value |
| `After`, `Before`, `Between` | Time‑based routing |

---

## 7️⃣ Essential Filters 🧰

| Filter                                     | What it does                     |
| ------------------------------------------ | -------------------------------- |
| `StripPrefix`                              | Remove N path segments           |
| `RewritePath`                              | Regex rewrite                    |
| `AddRequestHeader` / `RemoveRequestHeader` | Mutate headers                   |
| `Retry`                                    | Automatically retry failed calls |
| `CircuitBreaker`                           | Open/Close via Resilience4j      |
| `RequestRateLimiter`                       | Redis‑backed throttling          |
| `TokenRelay`                               | Forward OAuth2/JWT downstream    |

---

## 8️⃣ Security & Token Relay 🛡️

1. Gateway validates **JWT / OAuth2** once ✅
2. `TokenRelay` filter forwards token to micro‑services 🔑
3. Services trust `X-Forwarded-*` or the JWT itself 🤝

```yaml
default-filters:
  - TokenRelay
```

---

## 9️⃣ Reactive vs Blocking ⚡️ vs 💤

| Characteristic | Reactive (SCG)     | Blocking (e.g. Zuul 1) |
| -------------- | ------------------ | ---------------------- |
| Thread model   | Event‑loop (Netty) | Thread‑per‑request     |
| Performance    | Higher concurrency | Limited by threads     |
| Back‑pressure  | Built‑in           | Manual                 |
| Debugging      | Harder             | Easier                 |

---

## 🔟 Deployment & Docker 🐳

* Gateway listens on **:8080** (external) 🌐
* Internal routes use service DNS names (`patient-service:4000`) 🔗
* Place SSL termination **before** gateway when possible 🔒
* Horizontal scaling: run multiple gateway pods behind load balancer ⛵️

---

## 1️⃣1️⃣ Error Handling & Resilience 🚑

* **Timeouts**: `spring.cloud.gateway.httpclient.connect-timeout` ⏱️
* **Retry**: `Retry` filter with `retries`, `backoff` 🔄
* **CircuitBreaker**: graceful degradation 🛑
* **Fallback**: return uniform JSON error scheme 📄

---

## 1️⃣2️⃣ Junior / Intermediate Checklist ✅

* [x] Describe what an API Gateway does 🤓
* [x] Explain SCG’s reactive nature 🌊
* [x] Create a basic route (Path + URI) 🛣️
* [x] Apply `StripPrefix` & `RewritePath` 🖋️
* [x] Implement JWT auth & `TokenRelay` 🔐
* [x] Set up retries / circuit breakers 🚥
* [x] Understand Docker networking for gateway → services 🐳
* [x] Know where to place SSL termination 🔒

If you can tick every box, you’re **job‑ready** at Jr → Intermediate level! 🎉

---

## 1️⃣3️⃣ AI Prompt 🤖

> **Prompt:**
>
> "Teach me Spring Cloud Gateway for a junior–intermediate backend developer. Explain:
> • What an API Gateway is and why microservices need it
> • How Spring Cloud Gateway works in Spring Boot 4
> • What “reactive” means in this context
> • Routing, predicates, and filters (StripPrefix, RewritePath, TokenRelay)
> • Security patterns (JWT, OAuth2, Token Relay)
> • How the gateway communicates with services inside Docker
> • Best practices and common pitfalls
> • A simple mental model for understanding how requests flow through the gateway
> Use clear examples and production‑grade explanations."

---

✨ **End of Notes — Happy Coding!** ✨
