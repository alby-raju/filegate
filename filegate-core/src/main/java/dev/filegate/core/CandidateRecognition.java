package dev.filegate.core;

/** Strict offset-zero candidates. Leading data and full structure are not inspected. */
public final class CandidateRecognition {
    public static final int PREFIX_BYTES = 8;
    private CandidateRecognition() {}

    public static FormatCandidate recognize(byte[] prefix) {
        if (starts(prefix, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) return FormatCandidate.PNG;
        if (starts(prefix, 0xff, 0xd8, 0xff)) return FormatCandidate.JPEG;
        if (starts(prefix, 0x25, 0x50, 0x44, 0x46, 0x2d)) return FormatCandidate.PDF;
        if (starts(prefix, 0x50, 0x4b, 0x03, 0x04) || starts(prefix, 0x50, 0x4b, 0x05, 0x06)) {
            return FormatCandidate.ZIP_CONTAINER;
        }
        return FormatCandidate.UNKNOWN;
    }

    private static boolean starts(byte[] bytes, int... magic) {
        if (bytes.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) if ((bytes[i] & 255) != magic[i]) return false;
        return true;
    }
}
