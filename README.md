# RefIQ Platform (Reference Interval Quantification)

**RefIQ** is a backend platform designed to calculate clinical reference intervals (RI) from
large-scale laboratory datasets. It leverages **Spring Modulith** for modularity, **Data-Oriented
Programming (DOP)** for robustness, and integrates with an **R-based statistical engine (RefineR)**
via a Hexagonal Architecture.

---

## 🚀 Key Features

- **High-Throughput Ingestion**: Processes massive CSV files asynchronously using Virtual Threads
  and backpressure mechanisms.
- **Zero-Exception Flow**: Uses sealed interfaces to model business outcomes instead of control-flow
  exceptions.
- **R Integration**: Decoupled statistical analysis using Plumber and S3 presigned URLs (no heavy
  data transfer through Java).
- **Traceability**: End-to-end record tracking from raw CSV rows to final clinical results using
  unique `recordId`s.
- **Cloud Native**: Designed for AWS S3, fully compatible with LocalStack for local development.

---

## 🏗 Architecture

The project follows a **Modular Monolith** approach using Spring Modulith, enforcing strict
boundaries between functional areas.

### Core Modules

#### 1. Ingestion Module (`com.refiq.platform.ingestion`)

Responsible for receiving, validating, and normalizing raw clinical data.

- **Fire-and-Forget API**: The controller immediately offloads processing and returns
  `202 Accepted`.
- **Backpressure Control**: Semaphore-based concurrency limits for multipart uploads to S3,
  preventing OOM errors.
- **Canonical Normalization**: Transforms raw CSV inputs into a standardized format (EU/US separator
  detection, numeric validation) using high‑performance regex.
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

| Variable                             | Description                 | Default                 |
|--------------------------------------|-----------------------------|-------------------------|
| `refiq.storage.s3.bucket-name`       | S3 bucket for data          | `refiq-data`            |
| `refiq.storage.s3.endpoint`          | S3 endpoint URL             | `http://localhost:4566` |
| `plumber.api.url`                    | R engine base URL           | `http://localhost:8000` |
| `plumber.presigned.duration-minutes` | Presigned URL TTL (minutes) | `10`                    |

---

## ▶️ Running Locally (Docker-Only Flow)

This is the recommended method for **Frontend developers** or collaborators who do not want to set
up the **Java/R build environment**. The images are pulled directly from our private container
registry.

### 1. Authenticate with GitHub Packages (GHCR)

To download the **refiq-app** and **refiq-engine** images, you need a **Personal Access Token (
classic)**.

Go to:

Settings > Developer settings > Personal access tokens > Tokens (classic)

Generate a token with the permission:

```
read:packages
```

Then run the following command in your terminal (replace with your credentials):

```bash
echo "YOUR_TOKEN" | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
```

### 2. Start the Full Environment

Use the development configuration to start the **App**, **Database**, **S3 (LocalStack)** and the *
*R Engine**:

```bash
docker-compose -f docker-compose.yml up -d
```

### 3. Infrastructure Verification (S3)

If you are working on **Windows**, the S3 bucket might not be created automatically due to
line-ending conflicts in the startup script.

Run the following command to ensure the application can upload files:

```bash
docker exec -it refiq-localstack awslocal s3 mb s3://refiq-clinical-data-dev
```

### 4. Local Endpoints

- **App Backend / API**  
  http://localhost:8080

- **Swagger UI**  
  http://localhost:8080/swagger-ui/index.html

- **R Engine (Plumber)**  
  http://localhost:8000

- **LocalStack (S3)**  
  http://localhost:4566

## 🧩 Key Design Patterns

- **Hexagonal Architecture (Ports & Adapters)**: Domain logic isolated from infrastructure (S3,
  HTTP, R engine).
- **Data-Oriented Programming**: Heavy use of immutable records and sealed interfaces.
- **Virtual Threads**: Used in ingestion to scale blocking I/O without exhausting platform threads.

---

## ⚠️ Notes for Developers

- **S3 & Docker Networking**: `pathStyleAccessEnabled` is required for LocalStack + Docker
  interoperability.
- **CSV Format**: Separator auto‑detection (`;` vs `,`) is supported, but column order for Canonical
  CSV is strict.

---

© 2026 RefIQ Platform

