package com.gullakx.auth.web;

import com.gullakx.auth.service.AuthService;
import com.gullakx.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotBlank @Size(max = 120) String displayName) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record SessionResponse(String token, Long userId, String email) {
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<SessionResponse>> register(@Valid @RequestBody RegisterRequest request) {
        var session = auth.register(new AuthService.Registration(
                request.email(), request.password(), request.displayName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                new SessionResponse(session.token(), session.userId(), session.email()),
                "Registered"));
    }

    @PostMapping("/login")
    public ApiResponse<SessionResponse> login(@Valid @RequestBody LoginRequest request) {
        var session = auth.login(request.email(), request.password());
        return ApiResponse.success(
                new SessionResponse(session.token(), session.userId(), session.email()), "Signed in");
    }
}
