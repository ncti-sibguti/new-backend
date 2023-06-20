package ru.ncti.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.ncti.backend.entity.Role;

import java.util.Set;

/**
 * user: ichuvilin
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserViewDTO {
    private Long id;
    private String firstname;
    private String lastname;
    private String surname;
    private String email;
    private Set<Role> role;
    private String chat;
}
