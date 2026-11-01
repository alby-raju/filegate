package dev.filegate.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class Digests {
    private Digests() {}
    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
