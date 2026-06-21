package ma.hiresync.auth.service;

import ma.hiresync.auth.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtService reads its secret/expiration via @Value, which Spring only resolves
 * inside a running ApplicationContext. Since the logic itself is plain JJWT
 * usage with no other collaborator, we inject those two fields directly with
 * ReflectionTestUtils instead of paying for a full @SpringBootTest.
 */
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // 64 random bytes, base64-encoded — comfortably long enough for HS256/384/512.
        String secret = Base64.getEncoder().encodeToString(
                "test-secret-test-secret-test-secret-test-secret-test-secret-32".getBytes());
        ReflectionTestUtils.setField(jwtService, "secret", secret);
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3_600_000L); // 1h
    }

    private User user() {
        return User.builder()
                .id(UUID.randomUUID())
                .fullName("Othmane Sadiky")
                .email("othmane@example.com")
                .password("irrelevant")
                .role("CANDIDATE")
                .build();
    }

    @Test
    void generateToken_thenExtractEmail_roundTrips() {
        User user = user();
        String token = jwtService.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractEmail(token)).isEqualTo(user.getEmail());
    }

    @Test
    void generateToken_thenExtractUserId_roundTrips() {
        User user = user();
        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractUserId(token)).isEqualTo(user.getId());
    }

    @Test
    void isValid_correctUserAndUnexpiredToken_returnsTrue() {
        User user = user();
        String token = jwtService.generateToken(user);

        assertThat(jwtService.isValid(token, user)).isTrue();
    }

    @Test
    void isValid_tokenForDifferentUser_returnsFalse() {
        User issuedTo = user();
        String token = jwtService.generateToken(issuedTo);

        User someoneElse = User.builder()
                .id(UUID.randomUUID())
                .fullName("Quelqu'un d'autre")
                .email("autre@example.com")
                .password("x")
                .build();

        assertThat(jwtService.isValid(token, someoneElse)).isFalse();
    }

    @Test
    void isValid_expiredToken_returnsFalse() {
        ReflectionTestUtils.setField(jwtService, "expirationMs", -1_000L); // already expired
        User user = user();
        String token = jwtService.generateToken(user);

        assertThat(jwtService.isValid(token, user)).isFalse();
    }

    @Test
    void extractEmail_malformedToken_throws() {
        assertThatThrownBy(() -> jwtService.extractEmail("not-a-real-jwt"))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}
