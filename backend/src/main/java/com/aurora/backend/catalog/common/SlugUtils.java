package com.aurora.backend.catalog.common;

import java.text.Normalizer;
import java.util.Locale;

public final class SlugUtils {

    private SlugUtils() {
    }

    public static String from(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        if (normalized.isBlank()) {
            return "item";
        }

        return normalized;
    }

    public static String fromOptional(String slug, String fallback) {
        if (slug == null || slug.isBlank()) {
            return from(fallback);
        }

        return from(slug);
    }
}
