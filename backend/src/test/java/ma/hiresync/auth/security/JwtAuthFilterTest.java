package ma.hiresync.auth.security;

import ma.hiresync.auth.entity.User;
import ma.hiresync.auth.service.JwtService;
import ma.hiresync.auth.service.UserDetailsServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtService jwtService;
    @Mock private UserDetailsServiceImpl userDetailsService;

    private JwtAuthFilter filter() {
        return new JwtAuthFilter(jwtService, userDetailsService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User user() {
        return User.builder().id(UUID.randomUUID()).fullName("Othmane").email("othmane@example.com")
                .password("x").role("CANDIDATE").build();
    }

    @Test
    void noAuthorizationHeader_skipsAuthAndContinuesChain() throws Exception {
        var req = new MockHttpServletRequest();
        var res = new MockHttpServletResponse();
        var chain = mock(MockFilterChain.class);

        filter().doFilterInternal(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(req, res);
        verifyNoInteractions(jwtService);
    }

    @Test
    void headerWithoutBearerPrefix_skipsAuthAndContinuesChain() throws Exception {
        var req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Basic abc123");
        var res = new MockHttpServletResponse();
        var chain = mock(MockFilterChain.class);

        filter().doFilterInternal(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void validBearerToken_setsAuthenticationInSecurityContext() throws Exception {
        User user = user();
        var req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer valid-jwt");
        var res = new MockHttpServletResponse();
        var chain = mock(MockFilterChain.class);

        when(jwtService.extractEmail("valid-jwt")).thenReturn(user.getEmail());
        when(userDetailsService.loadUserByUsername(user.getEmail())).thenReturn(user);
        when(jwtService.isValid("valid-jwt", user)).thenReturn(true);

        filter().doFilterInternal(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(user);
        verify(chain).doFilter(req, res);
    }

    @Test
    void invalidToken_neverSetsAuthenticationButStillContinuesChain() throws Exception {
        User user = user();
        var req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer stale-jwt");
        var res = new MockHttpServletResponse();
        var chain = mock(MockFilterChain.class);

        when(jwtService.extractEmail("stale-jwt")).thenReturn(user.getEmail());
        when(userDetailsService.loadUserByUsername(user.getEmail())).thenReturn(user);
        when(jwtService.isValid("stale-jwt", user)).thenReturn(false);

        filter().doFilterInternal(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(req, res);
    }

    @Test
    void malformedToken_doesNotThrowAndLetsSecurityHandleIt() throws Exception {
        var req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer garbage");
        var res = new MockHttpServletResponse();
        var chain = mock(MockFilterChain.class);

        when(jwtService.extractEmail("garbage")).thenThrow(new io.jsonwebtoken.MalformedJwtException("bad token"));

        filter().doFilterInternal(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(req, res);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void existingAuthenticationInContext_isNeverOverwritten() throws Exception {
        User user = user();
        var req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer valid-jwt");
        var res = new MockHttpServletResponse();
        var chain = mock(MockFilterChain.class);

        var existingAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "someone-else", null);
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        when(jwtService.extractEmail("valid-jwt")).thenReturn(user.getEmail());

        filter().doFilterInternal(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existingAuth);
        verify(userDetailsService, never()).loadUserByUsername(any());
    }
}
