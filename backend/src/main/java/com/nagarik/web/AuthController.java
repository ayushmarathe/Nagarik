package com.nagarik.web;

import com.nagarik.domain.AppUser;
import com.nagarik.security.AuthPrincipal;
import com.nagarik.service.UserService;
import com.nagarik.web.dto.AuthResponse;
import com.nagarik.web.dto.DisplayNameRequest;
import com.nagarik.web.dto.LoginRequest;
import com.nagarik.web.dto.RegisterRequest;
import com.nagarik.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    /** Deliberately returns 200, not 201: nothing is created by signing in. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }

    /**
     * Who the token belongs to. The interface calls this once on load to find
     * out whether a stored token is still good, and gets the current name and
     * role back rather than trusting whatever it cached at sign-in time.
     *
     * There is no /logout. The token is stateless and signed, so the server has
     * nothing to forget - signing out means the client discarding it. Worth
     * knowing: a token that leaks stays valid until it expires, which is the
     * honest cost of not keeping sessions on the server.
     */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        AppUser user = userService.require(principal);
        return UserResponse.of(user);
    }

    @PatchMapping("/me")
    public UserResponse rename(@AuthenticationPrincipal AuthPrincipal principal,
                               @Valid @RequestBody DisplayNameRequest request) {
        return userService.rename(principal, request.displayName());
    }
}
