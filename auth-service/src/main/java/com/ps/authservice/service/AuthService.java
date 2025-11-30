package com.ps.authservice.service;

import com.ps.authservice.dto.LoginRequestDTO;
import com.ps.authservice.dto.SignUpDTO;
import com.ps.authservice.exception.UserExistsAlreadyException;
import com.ps.authservice.model.User;
import com.ps.authservice.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService
{
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    //Method to register
    public User register(SignUpDTO signUpDTO)
    {
        boolean userexists = userRepository.findUserByEmail(signUpDTO.getEmail()).isPresent();
        if(userexists)
        {
            throw new UserExistsAlreadyException("User Already Exists");
        }
        User user = User.builder()
                .userName(signUpDTO.getUsername())
                .email(signUpDTO.getEmail())
                .password(passwordEncoder.encode(signUpDTO.getPassword()))
                .role(User.Role.valueOf(signUpDTO.getRole()))
                .build();
        User savedUser = userRepository.save(user);

        return savedUser;
    }

    public String login(LoginRequestDTO loginRequestDTO)
    {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequestDTO.getEmail(),loginRequestDTO.getPassword()
                )
        );

        Optional<User> user = userRepository.findUserByEmail(loginRequestDTO.getEmail());

        if(user.isPresent())
        {
            return jwtService.generateToken(user.get().getEmail(),user.get().getRole().toString());
        }

        throw new UsernameNotFoundException("User not found");
    }
}
