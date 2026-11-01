package dev.filegate.core;

final class Names {
    private Names() {}

    static String token(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")) {
            throw new IllegalArgumentException("Expected a bounded ASCII identifier");
        }
        return value;
    }
}
