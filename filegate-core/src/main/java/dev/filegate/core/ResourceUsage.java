package dev.filegate.core;

/** Aggregate counters contain no artifact paths or content identifiers. */
public record ResourceUsage(int operations, long reservedStagingBytes, int pendingDeletions) {}
