
package com.example.demo.entity;

import lombok.*;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    // Catalyst system ROWID
    private Long id;

    private String username;

    private String email;

    private String passwordHash;

    private String fullName;

    private Role role;

    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Set<Warehouse> assignedWarehouses = new HashSet<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // Spring Security UserDetails implementation

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        if (this.role == null) {
            return List.of();
        }

        return List.of(
                new SimpleGrantedAuthority("ROLE_" + this.role.name()));
    }

    @Override
    public String getPassword() {
        return this.passwordHash;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return Boolean.TRUE.equals(this.isActive);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(this.isActive);
    }

    public enum Role {
        ADMIN,
        INVENTORY_MANAGER,
        SALES_MANAGER,
        WAREHOUSE_STAFF
    }
}
