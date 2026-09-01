package com.gullakx.wallet.config;

import com.gullakx.common.security.JwtVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Establishes who is calling. Nothing more.
 *
 * This filter answers "is this a real token, and whose is it?" — it does not
 * answer "may this person touch that wallet?". That second question depends on
 * the wallet being addressed, so it belongs next to the data, in
 * WalletController and WalletService.
 *
 * Keeping them apart matters because conflating them is how authorization holes
 * are born: a filter that has verified a signature feels like it has checked
 * something, and endpoints downstream start assuming a valid token means an
 * entitled caller.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    static final String PRINCIPAL_ATTRIBUTE = "gullakx.userId";

    private final JwtVerifier verifier;

    public JwtAuthFilter(JwtVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Long userId = verifier.subjectOf(header.substring(7).trim());
            if (userId != null) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute(PRINCIPAL_ATTRIBUTE, userId);
            }
        }
        // A bad token is left unauthenticated rather than rejected here, so the
        // security chain produces one consistent 401 for "no credentials" and
        // "bad credentials" alike.
        chain.doFilter(request, response);
    }
}
