package in.spendsmart.expense.controller;

import in.spendsmart.expense.security.CurrentUser;
import in.spendsmart.expense.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/device-token")
    public ResponseEntity<Void> updateDeviceToken(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody DeviceTokenRequest request
    ) {
        userService.updateDeviceToken(currentUser.getUserId(), request.token());
        return ResponseEntity.noContent().build();
    }

    public record DeviceTokenRequest(@NotBlank String token) {
    }
}
