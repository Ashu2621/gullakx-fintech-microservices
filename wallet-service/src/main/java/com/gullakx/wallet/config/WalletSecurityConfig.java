package com.gullakx.wallet.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gullakx.common.dto.ApiResponse;
import com.gullakx.common.security.JwtVerifier;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class WalletSecurityConfig {

    @Bean
    public JwtVerifier jwtVerifier(
            @Value("${gullakx.jwt.secret}") String secret,
            @Value("${gullakx.jwt.issuer:gullakx-auth}") String issuer) {
        return new JwtVerifier(secret, issuer);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                           ObjectMapper objectMapper) throws Exception {
        return http
                // Stateless and token-based: there is no session for a forged
                // cross-site request to ride on, so CSRF has nothing to protect.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Everything else needs a token. Default-deny: a new
                        // endpoint is protected the moment it is written, rather
                        // than the moment someone remembers to list it.
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        // Spring's default for an unauthenticated request is 403,
                        // which tells a client it is forbidden when what it
                        // actually needs to do is present a token. The two are
                        // different instructions: 401 means authenticate, 403
                        // means you did and it was not enough.
                        .authenticationEntryPoint((request, response, ex) ->
                                write(objectMapper, response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "AUTHENTICATION_REQUIRED", "A valid bearer token is required"))
                        .accessDeniedHandler((request, response, ex) ->
                                write(objectMapper, response, HttpServletResponse.SC_FORBIDDEN,
                                        "ACCESS_DENIED", "Not permitted")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
    }

    private static void write(ObjectMapper mapper, HttpServletResponse response,
                              int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getWriter(), ApiResponse.error(code, message));
    }
}
