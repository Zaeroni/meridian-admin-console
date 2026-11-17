package com.meridian.admin.web;

import com.meridian.admin.config.AuthUser;
import com.meridian.admin.service.AuthService;
import com.meridian.admin.web.Dtos.LoginRequest;
import com.meridian.admin.web.Dtos.LoginResponse;
import com.meridian.admin.web.Dtos.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    @SecurityRequirements // login is public, so no lock icon
    @Operation(summary = "Log in with email and password and receive a JWT")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.email(), request.password());
    }

    @GetMapping("/me")
    @Operation(summary = "The currently authenticated user")
    public UserDto me(@AuthenticationPrincipal AuthUser user) {
        return new UserDto(user.id(), user.email(), user.fullName(), user.role());
    }
}
