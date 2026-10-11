package com.myorg.board.upload;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ImageKeys {
    public static final String PREFIX = "uploads/";
    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png");

    private static final Pattern PATTERN = Pattern.compile(
            "^" + PREFIX + "[0-9a-f-]{36}\\.(" + String.join("|", EXTENSIONS.values()) + ")$");

    private ImageKeys() {}

    public static String extensionFor(String contentType) {
        return EXTENSIONS.get(contentType);
    }

    public static String newKey(String extension) {
        return PREFIX + UUID.randomUUID() + "." + extension;
    }

    public static boolean isValid(String key) {
        return key != null && PATTERN.matcher(key).matches();
    }
}