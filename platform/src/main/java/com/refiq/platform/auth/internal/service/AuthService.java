package com.refiq.platform.auth.internal.service;

import com.refiq.platform.auth.api.dto.RegisterUserRequest;
import com.refiq.platform.auth.api.dto.RegistrationResponse;
import com.refiq.platform.auth.api.dto.RegistrationResult;

import com.refiq.platform.auth.internal.domain.Credential;
import com.refiq.platform.auth.internal.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService { // Only controller can see this


    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final CredentialRepository credentialRepository;

    private final PasswordEncoder passwordEncoder;


    @Transactional
    public RegistrationResult register(RegisterUserRequest request) {
        // Classic SLFJ4 debugging
        log.debug("Procesando solicitud de registro para: {}", request.email());


        if (credentialRepository.existsByEmail(request.email())) {
            AuthLogEvent.REGISTRATION_FAILED_EMAIL_EXISTS.log(log, request.email());
            return new RegistrationResult.EmailAlreadyExists(request.email());
        }


        var newCredential = Credential.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        var saved = credentialRepository.save(newCredential);

        // Audit Log, our own logger for security, centralized logging aligned with DOP principles
        AuthLogEvent.USER_REGISTERED.log(log, saved.getEmail(), saved.getId());

        return new RegistrationResult.Success(
                new RegistrationResponse("Usuario registrado correctamente", saved.getId().toString())
        );
    }
}