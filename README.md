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

## Auth Module (`com.refiq.platform.auth`)

This module centralizes identity management, perimeter security, and access control for the platform. Its design materializes Data-Oriented Programming (DOP) principles applied to immutability and flow control.

- **Stateless Authentication (JWT):** Security is managed without state, disabling native sessions in the Spring Security configuration. Requests are authorized through custom filters that intercept and inspect JSON Web Tokens (JWT) and webhook API keys before reaching the controllers.

- **Flow Control with Sealed Interfaces:** The fundamental entity of this bounded context is the immutable `Credential` record. The design categorically rejects the use of exceptions (such as `EntityExistsException` or `BadCredentialsException`) to drive business logic. Instead, operations return algebraic types defined by sealed interfaces such as `RegistrationResult` and `LoginResult`. The controller processes these returns through pattern matching in exhaustive `switch` statements, which guarantees at compile time that every possible state has been handled.

- **Strict HTTP Contracts (Envelope Pattern):** To ensure consistent network output, the controller maps business results to sealed response interfaces, such as `LoginWebResponse`. This implements the envelope pattern, rigidly structuring successful responses (`Success`) and failures (`Failure`). This technique eliminates ambiguity and the need to use wildcard types (`?`) in `ResponseEntity` declarations.

- **Endpoint Protection:** Public access points integrate rate limiting to restrict attempts per IP address or email. This barrier intercepts brute-force attacks and mitigates user enumeration before consuming database resources or CPU cycles on password hashing.

- **Decoupling through Events:** After completing the atomic credential creation transaction, the module publishes a `UserRegisteredEvent`. This transactional event acts as an asynchronous contract, allowing independent modules (such as the user profiles module) to initialize their own states without creating code coupling.

## Ingestion Module (`com.refiq.platform.ingestion`)

This module is responsible for the reception, validation, and robust transfer of large volumes of clinical data to the Data Lake. Its design prioritizes I/O efficiency and isolation of physical resources.

- **Reception and I/O Isolation:** The controller intercepts the request and uses the `Analyte` enum as the single source of truth to validate and route files. Since processing is done in the background and the HTTP connection is immediately released with a 202 status code, the `MultipartFile` is copied to a temporary location on disk to avoid premature stream closures (`Stream Closed`). This resource is encapsulated in the `IngestionFile` record, which provides a `Supplier<InputStream>` and an explicit callback for destroying the temporary file once the transfer is complete.

- **Data-Oriented Design (DOP):** The philosophy of sealed interfaces for flow control is maintained. The service returns an `IngestionResult` with exhaustive states (`Success`, `InvalidFile`, `StorageUnavailable`), which the controller maps through pattern matching to a strict HTTP contract based on the envelope pattern (`IngestionWebResponse`).

- **Asynchronous Processing and Backpressure:** The multipart upload to the Bronze layer of the Data Lake is orchestrated using Java Virtual Threads (`ingestionExecutor`). To prevent RAM and network saturation when handling massive files, the service implements a backpressure mechanism using a semaphore (`uploadPermits`), concurrently limiting the chunks sent to storage.

- **Hexagonal Architecture (Storage):** Disk and network operations are abstracted through the output port `StoragePort`. Its main implementation, `S3StorageAdapter` (natively compatible with MinIO via AWS SDK v2), handles protocol particularities, such as the strict requirement to sort ETags in ascending order before completing the multipart file assembly.

- **Reactive Communication:** The ingestion lifecycle is communicated to the rest of the Modulith topology through domain events. On start, a `FileAcceptedEvent` is published to allow early tracking by other modules, and in case of a critical network error, an `IngestionFailedEvent` is emitted. Additionally, the service orchestrates external automation by triggering the Data Lake pipeline through a REST client once the file lands safely.

## Calculation Module (`com.refiq.platform.calculation`)

This module orchestrates the analytical core of the platform. Its responsibility is to capture data normalization events, delegate the mathematical workload to the R statistical engine, and asynchronously manage the state machine that reflects calculation progress for external clients.

- **Event-Driven Integration and Webhooks:** The calculation flow is triggered automatically. The `MinioWebhookController` receives push notifications (webhooks) from the Data Lake when a processed file lands in the Gold layer. To avoid blocking the storage event dispatcher, the controller immediately acknowledges the payload with an HTTP 200 OK and delegates the actual analysis to a Virtual Thread in the background.

- **Immutable State Machine (DOP):** Progress tracking is strictly modeled under Data-Oriented Programming principles. The sealed interface `CalculationState` defines the only possible states (`Pending`, `Processing`, `Success`, `Failed`). This approach eliminates the use of nullable columns and boolean flags in the database, guaranteeing at compile time that, for example, a success state always contains a resulting payload. The repository persists and rehydrates these transitions using pattern matching over jOOQ.

- **Idempotency and Concurrency Locks:** In an asynchronous architecture, protecting against duplicate events is vital. Upon receiving an Ingestion event, the system initializes the state with an `ON CONFLICT DO NOTHING` clause. Subsequently, before sending the request to R, the service executes an optimistic conditional update (`claimPendingCalculation`) to ensure that the same calculation is not processed concurrently by multiple threads.

- **Hexagonal Architecture (R Engine):** Communication with the independent statistical container is abstracted through the `AnalysisPort`. Its adapter (`PlumberAdapter`) is responsible for generating short-lived S3 Presigned URLs, allowing the engine to download the file without requiring permanent AWS credentials.

- **Resilience:** The adapter handles transient network failures using Spring's `@Retryable` mechanism with exponential backoff. However, it actively discriminates semantic errors: if the R engine rejects the calculation due to lack of statistical convergence (HTTP 422), it throws a business exception that stops useless retries and updates the state to `Failed`.

- **Asynchronous Output Contract:** Due to the unpredictable time taken by the statistical algorithm, the end client (Frontend) queries the result through the `ResultController` using the identifier generated during Ingestion. This controller exposes the state machine by mapping the sealed interfaces to standardized HTTP responses using the envelope pattern.

## User Module (`com.refiq.platform.user`)

This module is responsible for managing and exposing user profile information (such as the hospital or laboratory name and contact details). Although functionally it is currently the smallest module in the platform, its design demonstrates how Spring Modulith enables the development of highly decoupled domains.

- **Event-Driven Provisioning:** The module does not expose direct write services to create a profile. Instead, the `UserService` acts as an event-driven consumer within the application's asynchronous topology. Through the `@ApplicationModuleListener` annotation, the service intercepts the `UserRegisteredEvent` emitted by the Auth module. This guarantees that profile creation is a reactive process, avoiding structural coupling between security logic and identity management.

- **Transactional Idempotency:** Since retries can occur in event-based architectures, the insertion of the `UserProfile` record into the database is performed using an `ON CONFLICT DO NOTHING` clause via jOOQ. This ensures that multiple deliveries of the same registration event (at-least-once) do not generate errors or duplications in the system.

- **Data-Oriented Design:** Following the repository's overall philosophy, the main entity is an immutable record (`UserProfile`), devoid of persistence framework dependencies. The repository (`DbUserProfileRepository`) extracts data using strongly typed queries and explicitly maps it to the record's constructor, ensuring information integrity at compile time and eliminating the blind spots typical of runtime reflection.

- **Secure Exposure:** The REST controller (`UserController`) provides access to profile data. To guarantee information security, it does not require the client to send their identifier in the URL or request body. Instead, the unique identifier (UUID) is automatically injected from the active security context (`@AuthenticationPrincipal`), preventing any attempt at identity spoofing (IDOR).

## Structured Logging System (Observability)

The platform approaches observability by directly applying Data-Oriented Programming principles, treating system logs as immutable domain events rather than simple text strings scattered across services.

- **Catalogs via Sealed Interfaces:** Each module defines its possible traces through a centralized sealed interface (for example, `AuthLogEvent` or `CalculationLogEvent`). Every business event or technical error is modeled as a specific record implementing this interface.

- **Flow Control with Pattern Matching:** The sealed interfaces themselves expose a default method that evaluates the current instance through an exhaustive `switch`. This centralizes the definition of the severity level (`Info`, `Warn`, `Error`, `Debug`) and guarantees at compile time that every defined event has an associated treatment.

- **Structured Traces (Fluent API):** Instead of concatenating plain text, the implementation leverages SLF4J's Fluent Logging API (`logger.atInfo()`, `logger.atError()`, etc.). The operation context is injected directly as structured key-value pairs using `.addKeyValue()`.

- **Native Integration with the ELK Stack:** This technical decision greatly facilitates the export of logs to aggregation systems such as Elasticsearch, Logstash, and Kibana. By injecting data as key-value pairs, the system can emit native JSON. This eliminates the need to process regular expressions (Grok) to extract information, allowing Kibana to immediately index and filter by exact fields such as `user_id`, `s3_key`, or `ip_address`.

## Shared Module (`com.refiq.platform.shared`)

This domain acts as the technical substrate of the platform. Unlike the business modules, its purpose is to provide cross-cutting configurations and unify technical responses without coupling domain logic. It is mandatorily integrated into all integration tests through the custom meta-annotation `@RefiqModuleTest`.

- **Global Exception Handling:** The `GlobalExceptionHandler` component acts as an AOP interceptor to capture technical exceptions (such as malformed JSON or validation errors) that escape from the controllers. It translates these errors into a unified standard record (`ApiError`), ensuring that even infrastructure failures follow the Envelope Pattern used in business responses.

- **Lightweight Concurrency (Project Loom):** Asynchronous management is centralized in `AsyncConfig`. Instead of relying on traditional blocking Thread Pools, executors based on Java 21 Virtual Threads are defined (`Executors.newVirtualThreadPerTaskExecutor()`). These executors (`ingestionExecutor`, `webhookExecutor`) are injected into the services, facilitating high performance in I/O-bound operations and simplifying isolation during unit tests.

- **Dynamic Storage Infrastructure:** `S3Config` provides the synchronous and presigned URL (`S3Presigner`) clients required for AWS SDK v2. Its design incorporates specific properties to override endpoints (`presigned-endpoint`), resolving the routing disparity that occurs in local environments when a Docker container communicates with MinIO versus access through localhost.

- **Interactive API Documentation:** Through `OpenApiConfig`, the OpenAPI specification (Swagger) is automatically generated. To keep the interface clean and navigable, the configuration dynamically segments the endpoints into groups corresponding to each Modulith domain (Auth, Ingestion, Calculation, User).

- **Clean API Documentation (OpenAPI/Swagger):** To avoid visual pollution in the REST controllers and maintain a strict separation of concerns, the OpenAPI specification is defined in dedicated interfaces (for example, `AuthApi` and `IngestionApi`). The platform's controllers implement these interfaces, cleanly inheriting all annotation metadata such as `@Operation` and `@ApiResponses`. This approach allows for exhaustive documentation of the HTTP contracts —including literal JSON examples (`@ExampleObject`) for the different success or failure scenarios typified by the sealed interfaces— while the controller code remains focused exclusively on network mapping and business delegation.

## Main Data Flow

The platform employs an event-driven architecture to asynchronously process large volumes of clinical data. The lifecycle of a file, from ingestion to the calculation of its reference intervals, follows this orchestrated flow:

**1. Ingestion and Isolation (Bronze Layer):**

- The client sends a raw CSV file through the public endpoint (`/api/ingestion/upload`).
- The Ingestion module assigns a unique identifier (correlation UUID) and transfers the file in segments (multipart upload) to the Bronze layer of S3/MinIO.
- Upon completion of the upload, the Data Lake pipeline is asynchronously triggered and an internal `FileAcceptedEvent` is emitted. The Calculation module intercepts this event and registers the transaction in the database with an immutable `PENDING` state.

**2. External Transformation (Data Lake):**

- The external pipeline cleans and normalizes the raw CSV, transforming it into an optimized columnar format (Parquet).
- The resulting file is deposited in the Gold layer of S3/MinIO.

**3. Webhook Notification and Statistical Analysis:**

- The arrival of the file in the Gold layer triggers an automated webhook from MinIO to the `MinioWebhookController`.
- To avoid blocking, the controller responds immediately (HTTP 200) and delegates execution to a Virtual Thread. The service claims exclusive processing rights (optimistic locking) to guarantee idempotency.
- The R adapter generates an ephemeral Presigned URL and contacts the statistical engine (Plumber), passing it the relevant statistical parameters (for example, percentiles 0.025 and 0.975).
- The R container securely downloads the Parquet file, runs the refineR algorithm to estimate the actual reference intervals, and returns a JSON payload with the clinical results.

**4. Consolidation and Availability:**

- The Calculation module receives the response JSON. Following the DOP paradigm, it transitions the state machine to `SUCCESS` and stores the result directly in PostgreSQL, using native support for JSONB.
- In parallel, the original client can query the status of its request through asynchronous polling (`/api/v1/results/{uuid}`). Once the flow concludes, this endpoint exposes the final calculated reference interval.

## Development and Execution Environment

The platform isolates its infrastructure dependencies through Docker containers. The repository provides different orchestration files (docker-compose) designed for each stage of the application lifecycle.

### Production Deployment (`docker-compose.prod.yml`)

This file orchestrates the services with configurations oriented toward real deployments, incorporating additional security and proxying measures.

- **Network isolation:** The application container (`app`) does not expose port 8080 directly to the host network.
- **Reverse Proxy:** The `nginx-proxy` service (based on Nginx Proxy Manager) acts as the entry point, managing HTTP traffic on port 80 and HTTPS traffic on port 443. It also exposes port 81 to access the Nginx administration panel.
- **Secrets management:** The application and the databases (PostgreSQL and MinIO) securely pull their credentials from a hidden `.env` file.
- **Persistence:** Specific volumes are defined (`postgres_prod_data`, `minio_prod_data`) to safeguard the data, along with volumes to store the Nginx configuration and the Let's Encrypt certificates (`/etc/letsencrypt`).

To spin up the production environment in the background, run:

```bash
docker compose -f docker-compose.prod.yml up -d
```

### Local Environments (`docker-compose.local.yml` / `docker-compose.dev.yml`)

These files spin up the complete infrastructure required to run the platform locally, including PostgreSQL, MinIO, and the R Plumber engine.

- Unlike production, these environments do expose ports directly to the host to facilitate debugging. Port 8080 is mapped for the main API, 5432 for the database, 9000/9001 for the MinIO console, and 8000 for the R statistical engine.
- The local environment is fed by the credentials from your `.env` file.
- The dev environment has its environment variables explicitly injected (hardcoded) in the manifest itself, ideal for quick tests without configuring secrets.

To spin up the local environment interacting with the `.env` file, run:

```bash
docker compose -f docker-compose.local.yml up -d

### jOOQ Class Generation (Codegen)

This process uses an ephemeral, isolated database (`refiq_build_db`) exposed on port 5433. Its sole purpose is to apply the Flyway schema in a clean environment so that jOOQ can generate the persistence code; the container does not interact with development, testing, or production data.

The manual flow for class generation (necessary after adding or modifying SQL migrations) is as follows:

**1. Spin up the ephemeral database:** Start the temporary PostgreSQL container in the background.

```bash
docker compose -f docker-compose.codegen.yml up -d
```

**2. Generate the data model:** Invoke the Maven profile. Flyway will build the schema on the blank database and jOOQ will generate the classes in `target/generated-sources/jooq`.

```bash
mvn generate-sources -P codegen
```

**3. Destroy the temporary environment:** Shut down the container and destroy orphan volumes. The `-v` flag is critical in this step to erase the Flyway history and avoid checksum mismatch errors when editing local migration scripts.

```bash
docker compose -f docker-compose.codegen.yml down -v
```

## Testing and Quality Assurance

The repository's testing strategy prioritizes integration verification with real infrastructure, minimizing execution times through strict control of the Spring context and container lifecycle.

The testing ecosystem is structured around the following key practices:

- **Optimized Ephemeral Infrastructure (Singleton Pattern):** For tests that require databases or storage services, Testcontainers (PostgreSQL and LocalStack) is used. Instead of spinning up and tearing down containers for each test class, a Singleton pattern is employed through Java enums (`GlobalPostgresContainer`, `GlobalS3Container`). This ensures the infrastructure is started only once for the entire suite execution, sharing the network context and drastically reducing continuous integration (CI) times. Credentials and dynamic ports are cleanly injected into the Spring environment via `@DynamicPropertySource` in inheritable base classes.

- **Isolated Modular Tests (`@RefiqModuleTest`):** To verify business logic without loading the entire monolith, a custom meta-annotation is used that instructs Spring Modulith to load only the module under test along with the cross-cutting `shared` package. In these tests, the emission of domain events is rigorously validated by intercepting the bus with `PublishedEvents`, and the concurrency and idempotency of asynchronous listeners are verified with the support of libraries such as Awaitility.

- **Slice Testing:**

    - **Web Layer:** HTTP routing and envelope pattern mapping are verified through `@WebMvcTest` and MockMvc. The security context is controlled through base classes that isolate API Key filtering or mock the JWT cryptographic provider (`@MockitoBean`), allowing controllers to be tested without the cost of hash processing. Global error handling is tested in isolation (standalone setup).

    - **Persistence:** The database layer is validated with `@JooqTest`, interacting directly against the real PostgreSQL container. This guarantees that Data-Oriented transactions (such as `ON CONFLICT DO NOTHING`) work correctly under the relational engine without throwing unwanted exceptions.

    - **External Network:** Integration with the R statistical engine is tested through `@RestClientTest` paired with `MockRestServiceServer`. This allows emulating network latencies, confirming the resilience behavior (`@Retryable`) against 500 errors, and validating the correct translation of 422 failures into domain exceptions without depending on the external container. For more complex scenarios, base infrastructure with Spring Cloud Contract WireMock (`@AutoConfigureWireMock`) is available.

- **Pure Unit Tests:** Pure mathematical logic (such as the Bucket4j-based rate limiting algorithm) and hash validations are tested using plain Mockito (`@ExtendWith(MockitoExtension.class)`), guaranteeing millisecond feedback by not spinning up any Spring context.