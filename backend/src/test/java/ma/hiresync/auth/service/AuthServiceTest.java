package ma.hiresync.auth.service;

import ma.hiresync.auth.dto.LoginRequest;
import ma.hiresync.auth.dto.RegisterRequest;
import ma.hiresync.auth.entity.User;
import ma.hiresync.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService is constructor-injected (UserRepository, JwtService, PasswordEncoder,
 * AuthenticationManager), so every collaborator can be mocked individually —
 * none of register()/login() needs a database, an HTTP call, or a Spring context.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepo;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder encoder;
    @Mock private AuthenticationManager authManager;

    private AuthService authService;

    private AuthService newAuthService() {
        return new AuthService(userRepo, jwtService, encoder, authManager);
    }

    @Test
    void register_newEmail_createsUserAndReturnsToken() {
        authService = newAuthService();
        var req = new RegisterRequest("Othmane Sadiky", "othmane@example.com", "password123");

        when(userRepo.existsByEmail(req.email())).thenReturn(false);
        when(encoder.encode(req.password())).thenReturn("hashed-password");
        when(jwtService.generateToken(any(User.class))).thenReturn("signed-jwt");

        var response = authService.register(req);

        assertThat(response.token()).isEqualTo("signed-jwt");
        assertThat(response.fullName()).isEqualTo("Othmane Sadiky");
        assertThat(response.email()).isEqualTo("othmane@example.com");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("hashed-password");
        assertThat(saved.getValue().getEmail()).isEqualTo("othmane@example.com");
    }

    @Test
    void register_emailAlreadyInUse_throwsAndNeverSaves() {
        authService = newAuthService();
        var req = new RegisterRequest("Othmane Sadiky", "othmane@example.com", "password123");
        when(userRepo.existsByEmail(req.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already in use");

        verify(userRepo, never()).save(any());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void register_neverStoresThePasswordInPlainText() {
        authService = newAuthService();
        var req = new RegisterRequest("Candidat", "candidat@example.com", "superSecret123");
        when(userRepo.existsByEmail(req.email())).thenReturn(false);
        when(encoder.encode(req.password())).thenReturn("$2a$10$hashedvalue");
        when(jwtService.generateToken(any(User.class))).thenReturn("token");

        authService.register(req);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(saved.capture());
        assertThat(saved.getValue().getPassword())
                .isNotEqualTo(req.password())
                .isEqualTo("$2a$10$hashedvalue");
    }

    @Test
    void login_validCredentials_authenticatesAndReturnsToken() {
        authService = newAuthService();
        var req = new LoginRequest("othmane@example.com", "password123");
        User existing = User.builder()
                .id(UUID.randomUUID())
                .fullName("Othmane Sadiky")
                .email(req.email())
                .password("hashed")
                .build();

        when(userRepo.findByEmail(req.email())).thenReturn(Optional.of(existing));
        when(jwtService.generateToken(existing)).thenReturn("signed-jwt");

        var response = authService.login(req);

        assertThat(response.token()).isEqualTo("signed-jwt");
        assertThat(response.userId()).isEqualTo(existing.getId());
        verify(authManager).authenticate(any());
    }

    @Test
    void login_authenticationManagerRejectsCredentials_propagatesException() {
        authService = newAuthService();
        var req = new LoginRequest("othmane@example.com", "wrong-password");
        when(authManager.authenticate(any()))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("bad creds"));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);

        verify(jwtService, never()).generateToken(any());
    }
}
