package dev.filegate.core;

import java.util.Objects;

/** Explicit capability identity. Version identifies the installed inspector build. */
public record CheckDescriptor(String id, String version, String coverageProfile,
                              ExecutionMode executionMode) {
    public CheckDescriptor {
        id = Names.token(id);
        version = Names.token(version);
        coverageProfile = Names.token(coverageProfile);
        Objects.requireNonNull(executionMode, "executionMode");
    }
}
