package com.spendsense.user;

import com.spendsense.common.api.ApiResponse;
import com.spendsense.common.exception.ApiException;
import com.spendsense.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public UserController(UserRepository userRepository, CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me() {
        User user = userRepository.findById(currentUser.require().getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"));
        return ApiResponse.of(UserResponse.from(user));
    }
}
