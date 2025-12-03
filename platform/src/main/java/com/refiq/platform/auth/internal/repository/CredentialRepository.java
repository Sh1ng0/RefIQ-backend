package com.refiq.platform.auth.internal.repository;

import com.refiq.platform.auth.internal.domain.Credential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    boolean existsByEmail(String email);

    Optional<Credential> findByEmail(String email);

}

