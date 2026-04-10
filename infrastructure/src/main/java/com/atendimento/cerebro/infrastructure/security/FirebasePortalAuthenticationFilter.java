package com.atendimento.cerebro.infrastructure.security;

import com.atendimento.cerebro.application.port.out.PortalUserStorePort;
import com.atendimento.cerebro.infrastructure.security.firebase.FirebaseTokenVerifier;
import com.google.firebase.auth.FirebaseAuthException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class FirebasePortalAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebasePortalAuthenticationFilter.class);

    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final PortalUserStorePort portalUserStore;

    public FirebasePortalAuthenticationFilter(FirebaseTokenVerifier firebaseTokenVerifier, PortalUserStorePort portalUserStore) {
        this.firebaseTokenVerifier = firebaseTokenVerifier;
        this.portalUserStore = portalUserStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                try {
                    var decoded = firebaseTokenVerifier.verify(token);
                    String uid = decoded.uid();
                    var portalUser = portalUserStore.findByFirebaseUid(uid);
                    if (portalUser.isPresent()) {
                        var pu = portalUser.get();
                        var auth = new PortalAuthenticationToken(
                                pu.tenantId().value(), pu.firebaseUid(), pu.profileLevel());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } else {
                        var pending = new FirebasePendingInviteAuthenticationToken(uid);
                        pending.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(pending);
                    }
                } catch (FirebaseAuthException | IllegalArgumentException | IllegalStateException e) {
                    log.warn("Falha ao validar ID token Firebase: {}", e.getMessage());
                    SecurityContextHolder.clearContext();
                } catch (Exception e) {
                    log.warn("Falha ao validar ID token Firebase: {}", e.toString());
                    SecurityContextHolder.clearContext();
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
