package dev.filegate.formats.images;

/** All numeric limits are explicit deployment inputs, not recommended production defaults. */
public record ImageProfile(long maxInputBytes, long maxPixels, long maxDecodedBytes,
                           long maxMetadataBytes, int maxSegments) {
    public ImageProfile {
        if (maxInputBytes <= 0 || maxPixels <= 0 || maxDecodedBytes <= 0
                || maxMetadataBytes < 0 || maxSegments <= 0 || maxSegments > 1_000_000) {
            throw new IllegalArgumentException("Invalid image coverage envelope");
        }
    }
}
