package ma.hiresync.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.auth.dto.AuthResponse;
import ma.hiresync.auth.dto.GoogleAuthRequest;
import ma.hiresync.auth.dto.LoginRequest;
import ma.hiresync.auth.dto.RegisterRequest;
import ma.hiresync.auth.entity.User;
import ma.hiresync.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository        userRepo;
    private final JwtService            jwtService;
    private final PasswordEncoder       encoder;
    private final AuthenticationManager authManager;  // safe: no longer circular

    @Value("${hiresync.google.client-id}")
    private String googleClientId;

    private GoogleIdTokenVerifier googleVerifier;

    public AuthResponse register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.email()))
            throw new IllegalArgumentException("Email already in use");
        var user = User.builder()
                .fullName(req.fullName())
                .email(req.email())
                .password(encoder.encode(req.password()))
                .build();
        userRepo.save(user);
        return toResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        authManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        var user = (User) new UserDetailsServiceImpl(userRepo).loadUserByUsername(req.email());
        return toResponse(user);
    }

    /**
     * Verifies the ID token Google Identity Services handed the browser, then
     * finds the matching account or creates one — one button covers both
     * "log in" and "sign up". Google-created accounts get a random,
     * never-disclosed password (they always come back through this endpoint,
     * never the password login form).
     */
    public AuthResponse googleAuth(GoogleAuthRequest req) {
        GoogleIdToken.Payload payload = verifyGoogleToken(req.idToken());

        String email = payload.getEmail();
        if (email == null || !Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new IllegalArgumentException("Adresse email Google non vérifiée");
        }

        String picture = (String) payload.get("picture");

        User user = userRepo.findByEmail(email).map(existing -> {
            // Google profile photos change over time — keep ours in sync on every sign-in.
            if (picture != null && !picture.equals(existing.getAvatarUrl())) {
                existing.setAvatarUrl(picture);
                userRepo.save(existing);
            }
            return existing;
        }).orElseGet(() -> {
            String fullName = (String) payload.get("name");
            if (fullName == null || fullName.isBlank()) fullName = email.split("@")[0];
            var created = User.builder()
                    .fullName(fullName)
                    .email(email)
                    .password(encoder.encode(UUID.randomUUID().toString()))
                    .authProvider("GOOGLE")
                    .avatarUrl(picture)
                    .build();
            userRepo.save(created);
            log.info("Created account via Google sign-in: {}", email);
            return created;
        });

        return toResponse(user);
    }

    private GoogleIdToken.Payload verifyGoogleToken(String idToken) {
        try {
            if (googleVerifier == null) {
                googleVerifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(Collections.singletonList(googleClientId))
                        .build();
            }
            GoogleIdToken token = googleVerifier.verify(idToken);
            if (token == null) throw new IllegalArgumentException("Jeton Google invalide ou expiré");
            return token.getPayload();
        } catch (GeneralSecurityException | IOException | RuntimeException e) {
            // Catches malformed-token decoding errors too (e.g. not valid base64/JWT
            // structure) — never leak the raw exception class/message to the client.
            log.error("Google token verification failed: {}", e.getMessage());
            throw new IllegalArgumentException("Jeton Google invalide ou expiré");
        }
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(
            jwtService.generateToken(user),
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getAvatarUrl()
        );
    }
}
