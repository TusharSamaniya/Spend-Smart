package in.spendsmart.expense.controller;

import in.spendsmart.expense.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthService.TokenPair tokens = authService.register(
            request.name(),
            request.email(),
            request.password(),
            request.organizationName()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new RegisterResponse(tokens.accessToken(), tokens.refreshToken()));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.TokenPair tokens = authService.login(request.email(), request.password());
        return ResponseEntity.ok(new LoginResponse(tokens.accessToken(), tokens.refreshToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        String accessToken = authService.refreshAccessToken(request.refreshToken());
        return ResponseEntity.ok(new RefreshResponse(accessToken));
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 255) String organizationName
    ) {
    }

        public record RegisterResponse(String accessToken, String refreshToken) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record LoginResponse(String accessToken, String refreshToken) {
    }

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {
    }

    public record RefreshResponse(String accessToken) {
    }
}
