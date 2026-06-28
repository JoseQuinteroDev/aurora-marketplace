package com.aurora.backend.security.password;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the k-anonymity breached-password matcher (OWASP A07). No network: the
 * {@link HibpPasswordBreachChecker.RangeClient} is faked, so we verify (a) only the 5-char
 * SHA-1 prefix is sent, (b) a suffix match with a positive count is a breach, (c) a padded
 * zero-count suffix is NOT, and (d) the checker fails open on error / when disabled.
 */
class HibpPasswordBreachCheckerTest {

    private static final String PASSWORD = "password";
    private static final String HASH = HibpPasswordBreachChecker.sha1Hex(PASSWORD);
    private static final String PREFIX = HASH.substring(0, 5);
    private static final String SUFFIX = HASH.substring(5);

    @Test
    void sendsOnlyTheFiveCharPrefixAndDetectsAPositiveCountMatch() {
        AtomicReference<String> sentPrefix = new AtomicReference<>();
        HibpPasswordBreachChecker checker = new HibpPasswordBreachChecker(true, prefix -> {
            sentPrefix.set(prefix);
            // Mixed-case + a decoy line to prove case-insensitive matching and line parsing.
            return "0018A45C4D1DEF81644B54AB7F969B88D65:1\n"
                    + SUFFIX.toLowerCase(java.util.Locale.ROOT) + ":3730471\n";
        });

        assertThat(checker.isBreached(PASSWORD)).isTrue();
        assertThat(sentPrefix.get()).isEqualTo(PREFIX);
        assertThat(sentPrefix.get()).hasSize(5);
    }

    @Test
    void aPaddedZeroCountSuffixIsNotTreatedAsBreached() {
        HibpPasswordBreachChecker checker = new HibpPasswordBreachChecker(true, prefix -> SUFFIX + ":0\r\n");

        assertThat(checker.isBreached(PASSWORD)).isFalse();
    }

    @Test
    void absentSuffixIsNotBreached() {
        HibpPasswordBreachChecker checker = new HibpPasswordBreachChecker(true,
                prefix -> "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:9\nAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:2\n");

        assertThat(checker.isBreached(PASSWORD)).isFalse();
    }

    @Test
    void failsOpenWhenTheRangeClientThrows() {
        HibpPasswordBreachChecker checker = new HibpPasswordBreachChecker(true, prefix -> {
            throw new RuntimeException("HIBP unreachable");
        });

        assertThat(checker.isBreached(PASSWORD)).isFalse();
    }

    @Test
    void disabledCheckerNeverCallsTheClientAndReportsNotBreached() {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        HibpPasswordBreachChecker checker = new HibpPasswordBreachChecker(false, prefix -> {
            called.set(true);
            return SUFFIX + ":999\n";
        });

        assertThat(checker.isBreached(PASSWORD)).isFalse();
        assertThat(called.get()).isFalse();
    }

    @Test
    void blankOrNullPasswordSkipsTheClient() {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        HibpPasswordBreachChecker checker = new HibpPasswordBreachChecker(true, prefix -> {
            called.set(true);
            return "x";
        });

        assertThat(checker.isBreached("")).isFalse();
        assertThat(checker.isBreached(null)).isFalse();
        assertThat(called.get()).isFalse();
    }

    @Test
    void sha1HexIsUppercaseHexAndFortyChars() {
        assertThat(HASH).hasSize(40).matches("[0-9A-F]{40}");
        // Known vector: SHA-1("password") = 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8
        assertThat(HASH).isEqualTo("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8");
    }
}
