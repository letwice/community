package community.store.user.dto;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;

import community.store.user.domain.Role;

public record UpdateUserRolesRequest(@NotEmpty Set<Role> roles) {
}
