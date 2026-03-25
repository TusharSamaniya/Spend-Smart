package in.spendsmart.expense.service;

import in.spendsmart.expense.entity.Organization;
import in.spendsmart.expense.entity.User;
import in.spendsmart.expense.repository.OrganizationRepository;
import in.spendsmart.expense.repository.UserRepository;
import in.spendsmart.expense.security.JwtUtil;
import in.spendsmart.expense.controller.AuthController.RegisterResponse;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final JwtUtil jwtUtil;

    @Transactional
    public RegisterResponse register(String name, String email, String password) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("Email is already registered");
        }

        Organization organization = Organization.builder()
                .id(UUID.randomUUID())
                .name(name)
                .plan("free")
                .baseCurrency("INR")
                .build();
        organizationRepository.save(organization);

        User user = User.builder()
                .id(UUID.randomUUID())
                .orgId(organization.getId())
                .email(normalizedEmail)
                .passwordHash(BCrypt.hashpw(password, BCrypt.gensalt()))
                .role("admin")
                .defaultCurrency("INR")
                .build();
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail(), user.getId(), user.getOrgId(), user.getRole());
        return new RegisterResponse(token);
    }

    public String login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("Invalid email or password"));

        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            throw new AccessDeniedException("Invalid email or password");
        }

        return jwtUtil.generateToken(user.getEmail(), user.getId(), user.getOrgId(), user.getRole());
    }
}
