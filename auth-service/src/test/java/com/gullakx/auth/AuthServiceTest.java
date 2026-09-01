package com.gullakx.auth;

import com.gullakx.auth.domain.User;
import com.gullakx.auth.repository.UserRepository;
import com.gullakx.auth.service.AuthService;
import com.gullakx.auth.service.JwtIssuer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {

    @Autowired
    private AuthService auth;
    @Autowired
    private JwtIssuer jwt;
    @Autowired
    private UserRepository users;

    private static final String GOOD_PASSWORD = "correct-horse-battery-staple";

    private AuthService.Registration registration(String email) {
        return new AuthService.Registration(email, GOOD_PASSWORD, "Test User");
    }

    @Test
    @DisplayName("registration issues a token and never stores the password")
    void registerStoresOnlyAHash() {
        String email = "ana-" + System.nanoTime() + "@example.com";
        var session = auth.register(registration(email));

        assertThat(session.token()).isNotBlank();
        assertThat(session.email()).isEqualTo(email);

        User stored = users.findByEmail(email).orElseThrow();
        assertThat(stored.getPasswordHash())
                .as("BCrypt hash, not the password")
                .startsWith("$2")
                .doesNotContain(GOOD_PASSWORD);
    }

    @Test
    @DisplayName("email is stored case-insensitively so one person cannot hold two accounts")
    void emailIsNormalised() {
        String base = "Mixed-" + System.nanoTime() + "@Example.COM";
        auth.register(registration(base));

        assertThat(users.findByEmail(base.toLowerCase())).isPresent();
        assertThatThrownBy(() -> auth.register(registration(base.toUpperCase())))
                .hasMessageContaining("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    @DisplayName("a duplicate registration is refused")
    void duplicateEmail() {
        String email = "dup-" + System.nanoTime() + "@example.com";
        auth.register(registration(email));
        assertThatThrownBy(() -> auth.register(registration(email)))
                .hasMessageContaining("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    @DisplayName("concurrent registrations of one address create exactly one account")
    void concurrentRegistrationRace() throws Exception {
        String email = "race-" + System.nanoTime() + "@example.com";
        int threads = 6;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    try {
                        return auth.register(registration(email));
                    } catch (IllegalArgumentException expected) {
                        return expected;
                    }
                }));
            }
            start.countDown();

            long created = 0;
            for (Future<Object> f : futures) {
                if (f.get(30, TimeUnit.SECONDS) instanceof AuthService.Session) created++;
            }

            // An existsByEmail() check alone cannot produce this: every thread
            // can find the address free before any of them inserts. The UNIQUE
            // index is what makes it one.
            assertThat(created).isEqualTo(1);
            assertThat(users.findByEmail(email)).isPresent();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("login succeeds with the right password")
    void loginSucceeds() {
        String email = "login-" + System.nanoTime() + "@example.com";
        auth.register(registration(email));

        var session = auth.login(email, GOOD_PASSWORD);
        assertThat(session.token()).isNotBlank();
    }

    @Test
    @DisplayName("wrong password and unknown user give the same error")
    void failuresAreIndistinguishable() {
        String email = "enum-" + System.nanoTime() + "@example.com";
        auth.register(registration(email));

        // Distinct messages here would turn login into an account-enumeration
        // oracle: try an address, read the error, learn whether it is a customer.
        assertThatThrownBy(() -> auth.login(email, "wrong-password-entirely"))
                .hasMessageContaining("INVALID_CREDENTIALS");
        assertThatThrownBy(() -> auth.login("nobody-" + System.nanoTime() + "@example.com", GOOD_PASSWORD))
                .hasMessageContaining("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("short and oversized passwords are refused")
    void passwordPolicy() {
        assertThatThrownBy(() -> auth.register(
                new AuthService.Registration("short-" + System.nanoTime() + "@example.com", "tiny", "X")))
                .hasMessageContaining("PASSWORD_TOO_SHORT");

        // BCrypt silently ignores everything past 72 bytes, so a 200-character
        // passphrase would be no stronger than its first 72. Refusing is honest.
        assertThatThrownBy(() -> auth.register(
                new AuthService.Registration("long-" + System.nanoTime() + "@example.com", "x".repeat(200), "X")))
                .hasMessageContaining("PASSWORD_TOO_LONG");
    }

    @Test
    @DisplayName("issued tokens verify, and tampered ones do not")
    void tokenVerification() {
        String email = "jwt-" + System.nanoTime() + "@example.com";
        var session = auth.register(registration(email));

        Claims claims = jwt.verify(session.token());
        assertThat(claims.getSubject()).isEqualTo(String.valueOf(session.userId()));
        assertThat(claims.get("email")).isEqualTo(email);
        assertThat(claims.getExpiration()).isAfter(new java.util.Date());

        String[] parts = session.token().split("\\.");
        String forged = parts[0] + "." + parts[1] + ".AAAA" + parts[2].substring(4);
        assertThatThrownBy(() -> jwt.verify(forged)).isInstanceOf(JwtException.class);

        assertThatThrownBy(() -> jwt.verify("not.a.token")).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a secret shorter than the hash output is refused at startup")
    void weakSecretRefused() {
        // A short HS256 key weakens the signature, and it is exactly the kind of
        // value that arrives from a tutorial and is never revisited.
        assertThatThrownBy(() -> new JwtIssuer("too-short", 60, "gullakx-auth"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
