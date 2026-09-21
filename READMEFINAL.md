# RefIQ Platform
RefIQ Platform is the backend engine designed for processing and fitting clinical diagnostic data. Its main function is to ingest laboratory test results and delegate their analysis to a statistical engine in R. This engine implements the **refineR** algorithm (Ammer et al., 2021), an indirect approach that separates physiological from pathological distributions in real-world data (*Real-World Data*). This allows hospitals and laboratories to estimate accurate reference intervals without relying exclusively on samples from healthy individuals.

## Repository Purpose
From a software engineering perspective, the central purpose of this repository is to demonstrate the practical application of the **Data-Oriented Programming (DOP)** paradigm in modern Java backend development.

The architecture intentionally departs from traditional Object-Oriented Programming (OOP) patterns based on mutability and strongly encapsulated state. Instead, the design embraces immutability and a strict separation between data and the logic that operates on it. By modeling information transparently and treating data as immutable entities, the accidental complexity of the domain is drastically reduced.

This leads to more declarative, predictable, and side-effect-free code. As an additional benefit, this programming model is notably easier to analyze and reason about for both human developers and assistance tools based on large language models (LLMs), thus laying the conceptual foundations for the architectural decisions that structure the entire platform.


## Tech Stack

The platform is built on Java 21 and relies on a set of tools focused on robustness, static typing, and modular design. The main dependencies declared in the project are:

- **Core & Structure:** Spring Boot 3.4.12 and Spring Modulith 1.3.1. Spring Modulith provides the infrastructure to verify context separation and manage domain event publication internally. Lombok is included optionally to reduce boilerplate code.

- **Data Persistence:** The chosen relational database is PostgreSQL. Schema versioning is managed with Flyway, and strongly typed data access is handled through jOOQ. To decouple code generation from the standard packaging, the creation of jOOQ classes and records is managed on demand using a dedicated Maven profile and the docker-compose.codegen

- **Storage (S3/MinIO):** File handling is done through AWS SDK v2. The `s3`, `s3-presigner`, and `aws-crt-client` modules are included, the latter optimized for performance in asynchronous data transfers.

- **Security & Traffic Control:** Spring Security acts as the main barrier. Stateless authentication is implemented with the JJWT library (0.12.5). To prevent endpoint abuse, Bucket4j (8.10.0) is integrated for rate limiting.

- **Resilience & Observability:** Spring Retry is used for operations prone to transient failures, and Spring Boot Actuator together with AOP (Aspect-Oriented Programming) for telemetry and monitoring. API documentation is automatically generated with Springdoc OpenAPI 2.8.3.

- **Testing:** The testing ecosystem prioritizes real integration. Testcontainers (along with specific modules for PostgreSQL and LocalStack) is used to spin up real infrastructure during tests. HTTP calls to the R statistical engine are simulated using WireMock Standalone (3.10.0) and Spring Cloud Contract, supported by Mockito and JUnit Jupiter.
  
- **Analytical Engine (R & Plumber):** The statistical core of the platform resides in an independent service written in R. Through Plumber, a REST API is exposed that handles probabilistic analysis requests (`/calculate-ri`) and service availability monitoring (`/health`). For efficient dataset reading, the service uses the `arrow` library to process files in Parquet format. This data is securely ingested via S3 presigned URLs, downloading a temporary file to ensure read compatibility. After processing, the refineR algorithm estimates the reference intervals, and the response is structured and serialized with `jsonlite` to maintain a strict data contract with the main Java backend.

## Modular Architecture (Spring Modulith)

The platform adopts a modular monolith approach orchestrated through Spring Modulith. This decision allows establishing strict bounded contexts, offering the structural separation typical of a microservices architecture, but without the operational complexity or network overhead associated with early development stages. Should the platform's evolution require it, this design ensures that extracting any domain into an independent service is a straightforward process.

To guarantee this independence, the design imposes strict rules:

- **Dependency isolation:** Cross-service injection between different modules is forbidden.

- **Event-Driven Communication:** Inter-module interaction is based exclusively on the publication and listening of transactional domain events (Transactional Outbox pattern).

- **Decoupled execution:** Through events such as `UserRegisteredEvent` or `FileAcceptedEvent`, domains react to state changes reactively and asynchronously. This ensures that a failure in a secondary process (such as creating a user profile) does not compromise or block the transaction of the emitting domain (such as credential registration).