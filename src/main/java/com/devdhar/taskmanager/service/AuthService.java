package com.devdhar.taskmanager.service;

import com.devdhar.taskmanager.dto.AuthDtos.AuthResponse;
import com.devdhar.taskmanager.dto.AuthDtos.LoginRequest;
import com.devdhar.taskmanager.dto.AuthDtos.RegisterRequest;
import com.devdhar.taskmanager.entity.User;
import com.devdhar.taskmanager.exception.DuplicateResourceException;
import com.devdhar.taskmanager.repository.UserRepository;
import com.devdhar.taskmanager.security.JwtService;
import com.devdhar.taskmanager.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username already taken: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already registered: " + request.email());
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password())) // never store plain text
                .build();

        userRepository.save(user);

        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        String token = jwtService.generateToken(userDetails);
        return new AuthResponse(token, user.getUsername(), jwtService.getExpirationMs());
    }

    public AuthResponse login(LoginRequest request) {
        // Delegates to Spring Security's AuthenticationManager, which uses our
        // DaoAuthenticationProvider + PasswordEncoder under the hood to verify
        // the raw password against the BCrypt hash. Throws BadCredentialsException on mismatch.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalStateException("User vanished after authentication"));

        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        String token = jwtService.generateToken(userDetails);
        return new AuthResponse(token, user.getUsername(), jwtService.getExpirationMs());
    }
}
