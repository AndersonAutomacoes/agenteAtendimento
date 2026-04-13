package com.atendimento.cerebro.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            FirebasePortalAuthenticationFilter firebasePortalAuthenticationFilter,
            ProfileTierAuthorizationManager profileTierAuthorizationManager)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth -> auth.requestMatchers(HttpMethod.POST, "/v1/auth/register")
                                // AuthController valida Bearer + tipo de token; permitAll evita 403 do filtro antes do controller.
                                .permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/v1/whatsapp/webhook/**")
                                .permitAll()
                                // AuthController valida Bearer + portal vs convite pendente (401/403 com JSON).
                                .requestMatchers(HttpMethod.GET, "/v1/auth/me")
                                .permitAll()
                                .requestMatchers(
                                                "/api/v1/dashboard/**",
                                                "/api/v1/analytics/**",
                                                "/api/v1/appointments/**")
                                .access(profileTierAuthorizationManager)
                                .anyRequest()
                                .permitAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(
                        ex ->
                                ex.accessDeniedHandler(
                                        (request, response, accessDeniedException) -> {
                                            Authentication auth =
                                                    SecurityContextHolder.getContext().getAuthentication();
                                            boolean anonymous =
                                                    auth == null || auth instanceof AnonymousAuthenticationToken;
                                            response.setStatus(
                                                    anonymous
                                                            ? HttpServletResponse.SC_UNAUTHORIZED
                                                            : HttpServletResponse.SC_FORBIDDEN);
                                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                                            String body =
                                                    anonymous
                                                            ? "{\"error\":\"não autenticado\"}"
                                                            : "{\"error\":\"perfil insuficiente\"}";
                                            response.getWriter().write(body);
                                        }))
                .addFilterBefore(firebasePortalAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
