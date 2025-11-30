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
public class LoginRequestDTO
{
    @NotBlank(message = "Email Can't be empty in Login Request")
    @Email(message = "Email should be valid")
    private String email;
    @NotBlank(message = "Password can not be empty in Login Request")
    @Size(message = "Password must be at least 8 characters Long", min = 8)
    private String password;
    @NotBlank(message = "Specify the Role, Either USER or ADMIN")
    @Pattern(message = "Either USER or ADMIN",regexp = "^(USER|ADMIN)$")
    private String role;

}
