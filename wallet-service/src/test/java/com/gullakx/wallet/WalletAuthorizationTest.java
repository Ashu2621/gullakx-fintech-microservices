package com.gullakx.wallet;

import com.gullakx.wallet.service.WalletService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Authentication is not authorization.
 *
 * A verified signature proves who is calling. It says nothing about whether that
 * caller may touch the wallet they just named — and before these tests existed,
 * nothing checked. Any user with a valid token could empty any wallet by id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalletAuthorizationTest {

    private static final String SECRET = "test-secret-that-is-definitely-long-enough-for-hs256";
    private static final String ISSUER = "gullakx-auth-test";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private WalletService walletService;

    // Fresh user ids per test. openWallet is idempotent by (owner, currency),
    // so fixed ids would make each test inherit the balance the previous one
    // left behind - which is how a passing suite starts hiding real failures.
    private long ana;
    private long bilal;
    private Long anaWallet;
    private Long bilalWallet;

    @BeforeEach
    void openWallets() {
        long stamp = System.nanoTime() % 1_000_000_000L;
        ana = stamp;
        bilal = stamp + 1;
        anaWallet = walletService.openWallet(String.valueOf(ana), "INR", 100_00).getId();
        bilalWallet = walletService.openWallet(String.valueOf(bilal), "INR", 100_00).getId();
    }

    private static String tokenFor(long userId) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(ISSUER)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(key)
                .compact();
    }

    private static String bearer(long userId) {
        return "Bearer " + tokenFor(userId);
    }

    // ── Authentication ───────────────────────────────────────────────────────

    @Test
    @DisplayName("no token is refused")
    void anonymousRefused() throws Exception {
        mvc.perform(get("/api/v1/wallets/" + anaWallet))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a forged token is refused")
    void forgedTokenRefused() throws Exception {
        String good = tokenFor(ana);
        String[] parts = good.split("\\.");
        String forged = parts[0] + "." + parts[1] + ".AAAA" + parts[2].substring(4);

        mvc.perform(get("/api/v1/wallets/" + anaWallet).header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a token from another issuer is refused")
    void foreignIssuerRefused() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String other = Jwts.builder()
                .subject(String.valueOf(ana))
                .issuer("some-other-system")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(key)
                .compact();

        mvc.perform(get("/api/v1/wallets/" + anaWallet).header("Authorization", "Bearer " + other))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an expired token is refused")
    void expiredTokenRefused() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String stale = Jwts.builder()
                .subject(String.valueOf(ana))
                .issuer(ISSUER)
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key)
                .compact();

        mvc.perform(get("/api/v1/wallets/" + anaWallet).header("Authorization", "Bearer " + stale))
                .andExpect(status().isUnauthorized());
    }

    // ── Authorization ────────────────────────────────────────────────────────

    @Test
    @DisplayName("a caller can read their own wallet")
    void ownWalletReadable() throws Exception {
        mvc.perform(get("/api/v1/wallets/" + anaWallet).header("Authorization", bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanceMinor").value(100_00));
    }

    @Test
    @DisplayName("a valid token does not grant access to someone else's wallet")
    void otherWalletNotReadable() throws Exception {
        // Ana is fully authenticated. That is not the same as being entitled.
        mvc.perform(get("/api/v1/wallets/" + bilalWallet).header("Authorization", bearer(ana)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("WALLET_NOT_FOUND"));
    }

    @Test
    @DisplayName("someone else's statement is not readable")
    void otherStatementNotReadable() throws Exception {
        mvc.perform(get("/api/v1/wallets/" + bilalWallet + "/statement")
                        .header("Authorization", bearer(ana)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("WALLET_NOT_FOUND"));
    }

    @Test
    @DisplayName("money cannot be moved out of a wallet the caller does not own")
    void cannotTransferFromOthersWallet() throws Exception {
        long bilalBefore = walletService.get(bilalWallet).getBalanceMinor();

        mvc.perform(post("/api/v1/wallets/transfers")
                        .header("Authorization", bearer(ana))
                        .header("Idempotency-Key", "steal-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"sourceWalletId": %d, "destWalletId": %d, "amountMinor": 5000}
                                 """.formatted(bilalWallet, anaWallet)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_YOUR_WALLET"));

        // The assertion that matters: nothing moved.
        assertThat(walletService.get(bilalWallet).getBalanceMinor()).isEqualTo(bilalBefore);
    }

    @Test
    @DisplayName("a caller can move money out of their own wallet")
    void canTransferFromOwnWallet() throws Exception {
        mvc.perform(post("/api/v1/wallets/transfers")
                        .header("Authorization", bearer(ana))
                        .header("Idempotency-Key", "ok-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"sourceWalletId": %d, "destWalletId": %d, "amountMinor": 2500}
                                 """.formatted(anaWallet, bilalWallet)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        assertThat(walletService.get(anaWallet).getBalanceMinor()).isEqualTo(75_00);
    }

    @Test
    @DisplayName("opening a wallet takes its owner from the token, not the body")
    void ownershipCannotBeAssertedByTheClient() throws Exception {
        // The body carries an ownerId that the API no longer reads. If it did,
        // this request would create a wallet in Bilal's name that Ana controls.
        mvc.perform(post("/api/v1/wallets")
                        .header("Authorization", bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"ownerId": "%d", "currency": "USD", "openingBalanceMinor": 0}
                                 """.formatted(bilal)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ownerId").value(String.valueOf(ana)));
    }

    @Test
    @DisplayName("a missing idempotency key is rejected before anything happens")
    void idempotencyKeyRequired() throws Exception {
        mvc.perform(post("/api/v1/wallets/transfers")
                        .header("Authorization", bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"sourceWalletId": %d, "destWalletId": %d, "amountMinor": 100}
                                 """.formatted(anaWallet, bilalWallet)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a rejected transfer is 422, not 400 — the request was fine")
    void insufficientFundsIsUnprocessable() throws Exception {
        mvc.perform(post("/api/v1/wallets/transfers")
                        .header("Authorization", bearer(ana))
                        .header("Idempotency-Key", "poor-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"sourceWalletId": %d, "destWalletId": %d, "amountMinor": 999999}
                                 """.formatted(anaWallet, bilalWallet)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    @DisplayName("health and metrics stay open for probes")
    void actuatorOpen() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
