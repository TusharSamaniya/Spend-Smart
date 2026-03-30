package in.spendsmart.expense.config;

import in.spendsmart.expense.entity.Organization;
import in.spendsmart.expense.entity.User;
import in.spendsmart.expense.repository.OrganizationRepository;
import in.spendsmart.expense.repository.UserRepository;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    public static final String DEMO_ORG_NAME = "SpendSmart Demo Org";
    public static final String DEMO_PASSWORD = "test123";

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            String adminEmail = "admin@spendsmart.in";
            if (userRepository.findByEmail(adminEmail).isPresent()) {
                log.info("Demo users already exist, skipping");
                return;
            }

            Organization organization = organizationRepository.findByName(DEMO_ORG_NAME)
                    .orElseGet(() -> organizationRepository.save(Organization.builder()
                            .id(UUID.randomUUID())
                            .name(DEMO_ORG_NAME)
                            .plan("business")
                            .baseCurrency("INR")
                            .stateCode("27")
                            .build()));

            UUID orgId = organization.getId();

            String passwordHash = passwordEncoder.encode(DEMO_PASSWORD);

            User admin = buildUser(orgId, "admin@spendsmart.in", passwordHash, "admin");
            User manager = buildUser(orgId, "manager@spendsmart.in", passwordHash, "manager");
            User employee = buildUser(orgId, "employee@spendsmart.in", passwordHash, "employee");

            userRepository.save(admin);
            userRepository.save(manager);
            userRepository.save(employee);

            log.info("Successfully created demo users: admin, manager, employee");
        } catch (Exception exception) {
            log.error("Failed to create demo users", exception);
            throw exception;
        }
    }

    private User buildUser(UUID orgId, String email, String passwordHash, String role) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        return User.builder()
                .id(UUID.randomUUID())
                .orgId(orgId)
                .email(normalizedEmail)
                .passwordHash(passwordHash)
                .role(role)
                .defaultCurrency("INR")
                .build();
    }
}
