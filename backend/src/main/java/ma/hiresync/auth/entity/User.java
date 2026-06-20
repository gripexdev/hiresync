package ma.hiresync.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    /**
     * "LOCAL" (email/password) or "GOOGLE" — how this account was created.
     * Nullable at the DB level (not {@code nullable = false}) so adding this
     * column to the existing `users` table via ddl-auto: update doesn't fail
     * on rows that predate it; treat null as "LOCAL" wherever it's read.
     */
    @Builder.Default
    private String authProvider = "LOCAL";

    /**
     * Google's profile photo URL, captured at sign-in. Null for local accounts
     * (and for Google accounts with no photo) — the frontend falls back to a
     * generated avatar in that case.
     */
    private String avatarUrl;

    @Column(nullable = false)
    @Builder.Default
    private String role = "CANDIDATE";

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    // ── UserDetails ───────────────────────────────────────────────────────────
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
    @Override public String getUsername()                  { return email; }
    @Override public boolean isAccountNonExpired()         { return true; }
    @Override public boolean isAccountNonLocked()          { return true; }
    @Override public boolean isCredentialsNonExpired()     { return true; }
    @Override public boolean isEnabled()                   { return true; }
}
