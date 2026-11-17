package com.meridian.admin.config;

import com.meridian.admin.repo.ReviewerRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads "Authorization: Bearer ...", verifies it, then loads the reviewer to get the current role.
 * If anything is wrong the request simply stays unauthenticated and the entry point answers 401.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ReviewerRepository reviewers;

    public JwtAuthFilter(JwtService jwtService, ReviewerRepository reviewers) {
        this.jwtService = jwtService;
        this.reviewers = reviewers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(PREFIX)) {
            jwtService.verify(header.substring(PREFIX.length()).trim())
                    .flatMap(reviewers::findById)
                    .filter(r -> r.isActive())
                    .ifPresent(r -> {
                        var user = new AuthUser(r.getId(), r.getEmail(), r.getFullName(), r.getRole());
                        var auth = new UsernamePasswordAuthenticationToken(
                                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + r.getRole().name())));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }
}
