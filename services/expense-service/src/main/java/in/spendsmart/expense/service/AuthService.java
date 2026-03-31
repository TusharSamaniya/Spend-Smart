package in.spendsmart.expense.service;

import in.spendsmart.expense.entity.Organization;
import in.spendsmart.expense.entity.User;
import in.spendsmart.expense.repository.OrganizationRepository;
import in.spendsmart.expense.repository.UserRepository;
import in.spendsmart.expense.security.JwtUtil;
import io.jsonwebtoken.Claims;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    @Transactional
    public TokenPair register(String name, String email, String password, String organizationName) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        log.info("Register attempt for email: {}", normalizedEmail);

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("Email is already registered");
        }

        String normalizedOrgName = organizationName.trim();
        Organization savedOrganization = organizationRepository.findByName(normalizedOrgName)
                .orElseGet(() -> organizationRepository.save(
                        Organization.builder()
                                .id(UUID.randomUUID())
                                .name(normalizedOrgName)
                                .plan("free")
                                .baseCurrency("INR")
                                .build()
                ));

        String encodedPassword = passwordEncoder.encode(password);
        log.info("Password encoded for email: {}, hash starts with: {}", normalizedEmail, encodedPassword.substring(0, Math.min(20, encodedPassword.length())));

        User user = User.builder()
                .id(UUID.randomUUID())
            .orgId(savedOrganization.getId())
                .email(normalizedEmail)
                .passwordHash(encodedPassword)
                .role("admin")
                .defaultCurrency("INR")
                .build();
        User savedUser = userRepository.save(user);
        log.info("User registered successfully with email: {}", normalizedEmail);

        String accessToken = jwtUtil.generateToken(
            savedUser.getId(),
            savedUser.getOrgId(),
            savedUser.getEmail(),
            savedUser.getRole()
        );
        String refreshToken = jwtUtil.generateRefreshToken(
            savedUser.getId(),
            savedUser.getOrgId(),
            savedUser.getEmail(),
            savedUser.getRole()
        );

        return new TokenPair(accessToken, refreshToken);
    }

    public TokenPair login(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        log.info("Login attempt for email: {}", normalizedEmail);

        var userOptional = userRepository.findByEmail(normalizedEmail);
        log.info("User found: {}", userOptional.isPresent());

        User user = userOptional
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        boolean passwordMatches = passwordEncoder.matches(password, user.getPasswordHash());
        log.info("Password matches: {}", passwordMatches);

        if (!passwordMatches) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String accessToken = jwtUtil.generateToken(user.getId(), user.getOrgId(), user.getEmail(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getOrgId(), user.getEmail(), user.getRole());
        return new TokenPair(accessToken, refreshToken);
    }

    public String refreshAccessToken(String refreshToken) {
        if (!jwtUtil.isRefreshToken(refreshToken) || !jwtUtil.validateToken(refreshToken)) {
            throw new AccessDeniedException("Invalid refresh token");
        }

        Claims claims = jwtUtil.extractClaims(refreshToken);
        String userId = claims.get("userId", String.class);
        String orgId = claims.get("orgId", String.class);

        if (userId == null || orgId == null) {
            throw new AccessDeniedException("Invalid refresh token: missing userId or orgId claims");
        }

        return jwtUtil.generateToken(
                UUID.fromString(userId),
                UUID.fromString(orgId),
                claims.get("email", String.class),
                claims.get("role", String.class)
        );
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }
}
