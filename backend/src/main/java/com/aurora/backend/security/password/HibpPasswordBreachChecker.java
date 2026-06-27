package com.aurora.backend.security.password;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link PasswordBreachChecker} backed by the Have I Been Pwned "Pwned Passwords" range
 * API using <strong>k-anonymity</strong>: only the first 5 hex chars of the password's
 * SHA-1 are ever sent to the remote service — the full hash (and obviously the password)
 * never leaves the process. The API returns every breached-hash suffix sharing that prefix
 * with its breach count; we match the remaining 35 chars locally.
 *
 * <p>The remote call is abstracted behind {@link RangeClient} so the hashing/parsing logic
 * is unit-testable with no network. The checker is <strong>fail-open</strong> (see
 * {@link PasswordBreachChecker}) and honours an {@code enabled} flag so it can be turned
 * off per environment.
 */
public class HibpPasswordBreachChecker implements PasswordBreachChecker {

    private static final Logger log = LoggerFactory.getLogger(HibpPasswordBreachChecker.class);

    /**
     * Fetches the breach-range body for a 5-char SHA-1 prefix. The body is a newline-separated
     * list of {@code SUFFIX:COUNT} lines. Implementations should request padding so all
     * responses look alike on the wire. May throw on any transport error — the checker
     * treats that as fail-open.
     */
    @FunctionalInterface
    public interface RangeClient {
        String fetchRange(String prefix5);
    }

    private final boolean enabled;
    private final RangeClient rangeClient;

    public HibpPasswordBreachChecker(boolean enabled, RangeClient rangeClient) {
        this.enabled = enabled;
        this.rangeClient = rangeClient;
    }

    @Override
    public boolean isBreached(String rawPassword) {
        if (!enabled || rawPassword == null || rawPassword.isEmpty()) {
            return false;
        }
        try {
            String sha1 = sha1Hex(rawPassword);
            String prefix = sha1.substring(0, 5);
            String suffix = sha1.substring(5);
            String body = rangeClient.fetchRange(prefix);
            return suffixIsBreached(body, suffix);
        } catch (RuntimeException exception) {
            // Fail-open: a breach-corpus outage must never block registration/reset.
            log.warn("Breached-password check failed open ({}).", exception.toString());
            return false;
        }
    }

    /** Uppercase hex SHA-1, matching the HIBP range API's response format. */
    static String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-1 is mandated by the JLS to be present on every JVM.
            throw new IllegalStateException("SHA-1 unavailable", impossible);
        }
    }

    /**
     * The body has one {@code SUFFIX:COUNT} per line. A match requires {@code COUNT > 0}:
     * with response padding enabled the API injects decoy suffixes with a count of 0, so a
     * bare suffix match is not sufficient — only a positive count means a real breach hit.
     */
    static boolean suffixIsBreached(String body, String suffix) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        for (String line : body.split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            if (line.substring(0, colon).trim().equalsIgnoreCase(suffix)) {
                return parseCount(line.substring(colon + 1)) > 0;
            }
        }
        return false;
    }

    private static long parseCount(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException malformed) {
            // A line we can't parse the count of: treat as a hit only if a suffix matched —
            // be conservative and say "not breached" rather than guess. (Reached only for a
            // malformed API response.)
            return 0L;
        }
    }
}
