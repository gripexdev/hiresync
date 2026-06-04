package ma.hiresync.auth.dto;

import java.util.UUID;

public record AuthResponse(
        String token,
        UUID   userId,
        String fullName,
        String email
) {}
