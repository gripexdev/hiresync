package ma.hiresync.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /api/auth/google — the ID token Google Identity Services handed the browser. */
public record GoogleAuthRequest(
        @NotBlank(message = "Le jeton Google est requis") String idToken
) {}
