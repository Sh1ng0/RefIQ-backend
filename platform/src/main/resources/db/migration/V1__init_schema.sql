-- ---------------------------------------------------------
-- V1: Inicialización del esquema core (Auth y Users)
-- ---------------------------------------------------------

-- 1. Tabla de Credenciales (Módulo Auth)
CREATE TABLE refiq_credentials (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 2. Tabla de Perfiles de Usuario (Módulo User)
CREATE TABLE refiq_user_profiles (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    -- La clave foránea que une el perfil con su credencial (Shared Primary Key)
    CONSTRAINT fk_user_profile_credential FOREIGN KEY (id) REFERENCES refiq_credentials (id) ON DELETE CASCADE
);

-- 3. Tabla para el Transactional Outbox de Spring Modulith
-- Modulith usa esta tabla por debajo para guardar los eventos (como el UserRegisteredEvent)
CREATE TABLE event_publication (
    id UUID NOT NULL,
    listener_id VARCHAR(512) NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    serialized_event VARCHAR(4000) NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);


-- ---------------------------------------------------------
-- V2: Creación de tabla para resultados de cálculo (Buzón)
-- ---------------------------------------------------------

CREATE TABLE calculation_results (
    -- Usamos el mismo UUID (fileId) generado en la ingesta
    id UUID PRIMARY KEY,

    -- Estado del procesamiento (PENDING, SUCCESS, FAILED)
    status VARCHAR(50) NOT NULL,

    -- El JSON final mapeado desde R (Usamos JSONB para máximo rendimiento en Postgres)
    payload JSONB,

    -- Mensajes de error en caso de fallo en Data Lake o R
    error_message TEXT
);