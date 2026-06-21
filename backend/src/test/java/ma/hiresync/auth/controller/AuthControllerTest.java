package ma.hiresync.auth.controller;

import ma.hiresync.auth.dto.AuthResponse;
import ma.hiresync.auth.dto.GoogleAuthRequest;
import ma.hiresync.auth.dto.LoginRequest;
import ma.hiresync.auth.dto.RegisterRequest;
import ma.hiresync.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;

    private AuthController controller() {
        return new AuthController(authService);
    }

    @Test
    void register_delegatesToServiceAndReturns200WithItsResponse() {
        var req = new RegisterRequest("Othmane", "othmane@example.com", "password123");
        var expected = new AuthResponse("jwt", UUID.randomUUID(), "Othmane", "othmane@example.com", null);
        when(authService.register(req)).thenReturn(expected);

        var response = controller().register(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void login_delegatesToServiceAndReturns200WithItsResponse() {
        var req = new LoginRequest("othmane@example.com", "password123");
        var expected = new AuthResponse("jwt", UUID.randomUUID(), "Othmane", "othmane@example.com", null);
        when(authService.login(req)).thenReturn(expected);

        var response = controller().login(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void google_delegatesToServiceAndReturns200WithItsResponse() {
        var req = new GoogleAuthRequest("google-id-token");
        var expected = new AuthResponse("jwt", UUID.randomUUID(), "Othmane", "othmane@example.com", "photo.jpg");
        when(authService.googleAuth(req)).thenReturn(expected);

        var response = controller().google(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }
}
