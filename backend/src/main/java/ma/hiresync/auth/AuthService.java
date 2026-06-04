package ma.hiresync.auth;

import lombok.RequiredArgsConstructor;
import ma.hiresync.auth.dto.AuthResponse;
import ma.hiresync.auth.dto.LoginRequest;
import ma.hiresync.auth.dto.RegisterRequest;
import ma.hiresync.auth.entity.User;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository        userRepo;
    private final JwtService            jwtService;
    private final PasswordEncoder       encoder;
    private final AuthenticationManager authManager;  // safe: no longer circular

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

    private AuthResponse toResponse(User user) {
        return new AuthResponse(
            jwtService.generateToken(user),
            user.getId(),
            user.getFullName(),
            user.getEmail()
        );
    }
}
