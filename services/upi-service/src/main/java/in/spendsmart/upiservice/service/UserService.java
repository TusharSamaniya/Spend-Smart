package in.spendsmart.upiservice.service;

import in.spendsmart.upiservice.entity.User;
import in.spendsmart.upiservice.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public Optional<User> findByUpiId(String upiId) {
        if (!StringUtils.hasText(upiId)) {
            return Optional.empty();
        }
        return userRepository.findByUpiIdsContaining(upiId.trim());
    }
}
