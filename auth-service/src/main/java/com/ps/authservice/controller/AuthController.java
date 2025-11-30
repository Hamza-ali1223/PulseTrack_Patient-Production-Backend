package com.ps.authservice.controller;

import com.ps.authservice.dto.LoginRequestDTO;
import com.ps.authservice.dto.LoginResponseDTO;
import com.ps.authservice.dto.SignUpDTO;
import com.ps.authservice.model.User;
import com.ps.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "This is our Auth Controller where we have Sign up and Login Endpoints"
,
        description = """
                AuthController — REST controller for user authentication: handles registration.
                """
)
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<User> register(@Valid @RequestBody SignUpDTO request) {
       User user = authService.register(request);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        String token = authService.login(request);
        return ResponseEntity.ok(LoginResponseDTO.builder().status("Authenticated").token(token).build());
    }
}
