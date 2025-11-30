package com.ps.authservice.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignUpDTO
{
    @NotBlank(message = "Email should not be blank")
    @Email(message = "Email should be valid")
    private String email;
    @NotBlank(message = "Username should not be blank")
    private String username;
    @NotBlank(message = "Password Can not be empty")
    @Size(message = "Password must be at least 8 characters Long", min = 8)
    private String password;
    @NotBlank(message = "Role should be either USER or ADMIN")
    @Pattern(message = "Either USER or ADMIN",regexp = "^(USER|ADMIN)$")
    private String role;
}
