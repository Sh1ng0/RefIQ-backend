
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


    CONSTRAINT fk_user_profile_credential FOREIGN KEY (id) REFERENCES refiq_credentials (id) ON DELETE CASCADE
);

-- 3. Tabla para el Transactional Outbox de Spring Modulith

CREATE TABLE event_publication (
    id UUID NOT NULL,
    listener_id VARCHAR(512) NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    serialized_event VARCHAR(4000) NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);



CREATE TABLE calculation_results (

    id UUID PRIMARY KEY,

    -- Estado del procesamiento (PENDING, SUCCESS, FAILED)
    status VARCHAR(50) NOT NULL,


    payload JSONB,


    error_message TEXT
);


-- Tabla de archivo para los eventos completados de Spring Modulith
CREATE TABLE event_publication_archive (
    id UUID NOT NULL,
    listener_id VARCHAR(512) NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    serialized_event VARCHAR(4000) NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);