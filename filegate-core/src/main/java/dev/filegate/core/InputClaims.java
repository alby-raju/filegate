package dev.filegate.core;

/** Untrusted, bounded metadata. Neither field is used as a staging path. */
public record InputClaims(String filename, String mediaType) {
    public static final InputClaims NONE = new InputClaims("", "");

    public InputClaims {
        if (filename == null || mediaType == null || filename.length() > 256 || mediaType.length() > 128
                || filename.chars().anyMatch(Character::isISOControl)
                || mediaType.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid or unbounded input claims");
        }
    }
}
