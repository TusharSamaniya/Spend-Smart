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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    @Transactional
    public TokenPair register(String name, String email, String password, String organizationName) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
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

        User user = User.builder()
                .id(UUID.randomUUID())
            .orgId(savedOrganization.getId())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(password))
                .role("admin")
                .defaultCurrency("INR")
                .build();
        User savedUser = userRepository.save(user);

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
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
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
        return jwtUtil.generateToken(
                UUID.fromString(claims.get("userId", String.class)),
                UUID.fromString(claims.get("orgId", String.class)),
                claims.get("email", String.class),
                claims.get("role", String.class)
        );
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }
}
