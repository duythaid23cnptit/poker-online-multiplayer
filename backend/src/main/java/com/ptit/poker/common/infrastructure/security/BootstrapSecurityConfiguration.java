package com.ptit.poker.common.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Temporary bootstrap-only policy. Phase 3 will replace it with the approved
 * authentication and authorization design; no fake JWT behavior belongs here.
 */
@Configuration
public class BootstrapSecurityConfiguration {

    @Bean
    SecurityFilterChain bootstrapSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}

