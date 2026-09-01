package com.gullakx.auth.service;

import com.gullakx.auth.domain.User;
import com.gullakx.auth.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwt;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtIssuer jwt) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
    }

    public record Registration(String email, String password, String displayName) {
    }

    public record Session(String token, Long userId, String email) {
    }

    @Transactional
    public Session register(Registration registration) {
        String email = normalise(registration.email());
        requireUsablePassword(registration.password());

        try {
            User user = users.save(new User(
                    email,
                    passwordEncoder.encode(registration.password()),
                    registration.displayName()));
            return new Session(jwt.issue(user.getId(), user.getEmail()), user.getId(), user.getEmail());
        } catch (DataIntegrityViolationException duplicate) {
            // The UNIQUE index, not a prior existsByEmail() check: two
            // simultaneous registrations can both find the address free.
            throw new IllegalArgumentException("EMAIL_ALREADY_REGISTERED");
        }
    }

    /**
     * Login failures are deliberately indistinguishable.
     *
     * "No such user" and "wrong password" as separate errors turn the login
     * endpoint into a way to enumerate which email addresses hold accounts.
     * The password is also verified even when no user was found, so the two
     * paths take comparable time and the answer cannot be inferred from how
     * quickly it arrives.
     */
    @Transactional(readOnly = true)
    public Session login(String rawEmail, String password) {
        String email = normalise(rawEmail);
        User user = users.findByEmail(email).orElse(null);

        boolean ok = user != null
                ? passwordEncoder.matches(password, user.getPasswordHash())
                : passwordEncoder.matches(password, DUMMY_HASH) && false;

        if (!ok) {
            throw new IllegalArgumentException("INVALID_CREDENTIALS");
        }
        return new Session(jwt.issue(user.getId(), user.getEmail()), user.getId(), user.getEmail());
    }

    /** A real BCrypt hash of a value nobody knows, used to keep timing even. */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private static String normalise(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("EMAIL_REQUIRED");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static void requireUsablePassword(String password) {
        if (password == null || password.length() < 12) {
            // Length beats composition rules: it is the property that actually
            // costs an attacker time, and it does not push people toward
            // "Passw0rd!" to satisfy a symbol requirement.
            throw new IllegalArgumentException("PASSWORD_TOO_SHORT");
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            // BCrypt silently truncates at 72 bytes. Rejecting is honest;
            // truncating means the tail of a long passphrase does nothing.
            throw new IllegalArgumentException("PASSWORD_TOO_LONG");
        }
    }
}
