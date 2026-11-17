package com.meridian.admin.service;

import com.meridian.admin.config.JwtService;
import com.meridian.admin.repo.ReviewerRepository;
import com.meridian.admin.web.Dtos.LoginResponse;
import com.meridian.admin.web.Dtos.UserDto;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final ReviewerRepository reviewers;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    /** Compared against when the email is unknown, so timing does not reveal which emails exist. */
    private final String dummyHash;

    public AuthService(ReviewerRepository reviewers, PasswordEncoder encoder, JwtService jwt) {
        this.reviewers = reviewers;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    public LoginResponse login(String email, String password) {
        var reviewer = reviewers.findByEmailIgnoreCase(email.trim()).filter(r -> r.isActive());
        boolean ok = encoder.matches(password, reviewer.map(r -> r.getPasswordHash()).orElse(dummyHash));
        if (reviewer.isEmpty() || !ok) {
            throw new BadCredentialsException("Invalid email or password");
        }
        var r = reviewer.get();
        var issued = jwt.issue(r.getId());
        return new LoginResponse(issued.token(), issued.expiresAt(),
                new UserDto(r.getId(), r.getEmail(), r.getFullName(), r.getRole()));
    }
}
