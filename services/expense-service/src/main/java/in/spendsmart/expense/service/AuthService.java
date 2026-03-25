package in.spendsmart.expense.service;

import in.spendsmart.expense.entity.User;
import in.spendsmart.expense.repository.UserRepository;
import in.spendsmart.expense.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public String login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("Invalid email or password"));

        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            throw new AccessDeniedException("Invalid email or password");
        }

        return jwtUtil.generateToken(user.getEmail(), user.getId(), user.getOrgId(), user.getRole());
    }
}
