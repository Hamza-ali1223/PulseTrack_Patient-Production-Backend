package com.ps.authservice.service;

import com.ps.authservice.model.User;
import com.ps.authservice.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    //The Job of this method is to fetch user from the Database
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException
    {
        Optional<User> userFromDb = userRepository.findUserByEmail(email);
        if (userFromDb.isEmpty()) {
            throw new UsernameNotFoundException("User not found");
        }
        User fetchedUser = userFromDb.get();

        return fetchedUser;

    }
}
