package com.atendimento.cerebro.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Define/limpa TenantContext para cada request HTTP.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof PortalAuthenticationToken portal && auth.isAuthenticated()) {
                TenantContext.setTenantId(portal.getTenantId());
            } else {
                TenantContext.clear();
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
