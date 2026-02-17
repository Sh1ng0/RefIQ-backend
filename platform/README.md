# RefIQ Platform (Reference Interval Quantification)

**RefIQ** is a backend platform designed to calculate clinical reference intervals (RI) from large-scale laboratory datasets. It leverages **Spring Modulith** for modularity, **Data-Oriented Programming (DOP)** for robustness, and integrates with an **R-based statistical engine (RefineR)** via a Hexagonal Architecture.

---

## 🚀 Key Features

- **High-Throughput Ingestion**: Processes massive CSV files asynchronously using Virtual Threads and backpressure mechanisms.
- **Zero-Exception Flow**: Uses sealed interfaces to model business outcomes instead of control-flow exceptions.
- **R Integration**: Decoupled statistical analysis using Plumber and S3 presigned URLs (no heavy data transfer through Java).
- **Traceability**: End-to-end record tracking from raw CSV rows to final clinical results using unique `recordId`s.
- **Cloud Native**: Designed for AWS S3, fully compatible with LocalStack for local development.

---

## 🏗 Architecture

The project follows a **Modular Monolith** approach using Spring Modulith, enforcing strict boundaries between functional areas.

### Core Modules

#### 1. Ingestion Module (`com.refiq.platform.ingestion`)

Responsible for receiving, validating, and normalizing raw clinical data.

- **Fire-and-Forget API**: The controller immediately offloads processing and returns `202 Accepted`.
- **Backpressure Control**: Semaphore-based concurrency limits for multipart uploads to S3, preventing OOM errors.
- **Canonical Normalization**: Transforms raw CSV inputs into a standardized format (EU/US separator detection, numeric validation) using high‑performance regex.
- **Storage Strategy**: Stores both:
  - **RAW** files (auditability)
  - **CANONICAL** files (R consumption)

#### 2. Calculation Module (`com.refiq.platform.calculation`)

Orchestrates the statistical analysis by communicating with the R engine.

- **Smart Orchestration**: Generates an S3 presigned URL so the R container downloads data directly.
- **Sealed Results Model**:
  - `Success` → Calculated reference ranges
  - `DataInconsistency` (422) → Statistically invalid data (e.g. non‑converging algorithm)
  - `EngineUnavailable` (503) → R engine unavailable or failing

#### 3. Auth Module (`com.refiq.platform.auth`) *(In Progress)*

Handles user registration, authentication, and security context management.

---

## 🛠 Tech Stack

- **Language**: Java 21 (records, sealed classes, virtual threads)
- **Framework**: Spring Boot 3.2+ (Spring Modulith)
- **Storage**: AWS S3 (prod) / LocalStack (dev)
- **Statistical Engine**: R (RefineR) exposed via Plumber
- **Build Tool**: Maven

---

## 🔌 API Reference

### Ingestion API

**Upload a CSV file for processing**

```http
POST /api/ingestion/upload
Content-Type: multipart/form-data
```

**Response**

```http
202 Accepted
```

Returns a tracking ID.

---

### Calculation API

**Trigger the RefineR algorithm on a stored canonical file**

```http
POST /api/v1/calculations/run
Content-Type: application/json
```

**Request Body**

```json
{
  "s3Key": "canonical/file-uuid.csv",
  "percentileLow": 0.025,
  "percentileHigh": 0.975,
  "testCode": "LOINC-123"
}
```

---

## ⚙️ Configuration & Setup

### Prerequisites

- Java 21 JDK
- Docker & Docker Compose (LocalStack + R/Plumber)
- Maven

### Environment Variables

The application uses `application.properties` with support for external configuration.

| Variable | Description | Default |
|--------|------------|---------|
| `refiq.storage.s3.bucket-name` | S3 bucket for data | `refiq-data` |
| `refiq.storage.s3.endpoint` | S3 endpoint URL | `http://localhost:4566` |
| `plumber.api.url` | R engine base URL | `http://localhost:8000` |
| `plumber.presigned.duration-minutes` | Presigned URL TTL (minutes) | `10` |

---

## ▶️ Running Locally

### Start Infrastructure

Starts LocalStack (S3) and the R Plumber container.

```bash
docker-compose up -d
```

### Run the Application

```bash
./mvnw spring-boot:run
```

---

## 🧩 Key Design Patterns

- **Hexagonal Architecture (Ports & Adapters)**: Domain logic isolated from infrastructure (S3, HTTP, R engine).
- **Data-Oriented Programming**: Heavy use of immutable records and sealed interfaces.
- **Virtual Threads**: Used in ingestion to scale blocking I/O without exhausting platform threads.

---

## ⚠️ Notes for Developers

- **S3 & Docker Networking**: `pathStyleAccessEnabled` is required for LocalStack + Docker interoperability.
- **CSV Format**: Separator auto‑detection (`;` vs `,`) is supported, but column order for Canonical CSV is strict.

---

© 2026 RefIQ Platform

