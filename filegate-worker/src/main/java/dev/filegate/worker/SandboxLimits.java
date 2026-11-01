package dev.filegate.worker;

/** Per-worker OS envelope; multiply by concurrent workers when sizing the host. */
public record SandboxLimits(long memoryBytes, long scratchBytes, int pids, int cpuMillisPerSecond) {
    public SandboxLimits {
        if (memoryBytes < 64L * 1024 * 1024 || scratchBytes < 1024 * 1024
                || scratchBytes > memoryBytes / 4 || pids < 32 || pids > 1024
                || cpuMillisPerSecond < 100 || cpuMillisPerSecond > 4000) {
            throw new IllegalArgumentException("Invalid sandbox resource envelope");
        }
    }

    public String[] arguments() {
        return new String[] {Long.toString(memoryBytes), Long.toString(scratchBytes),
                Integer.toString(pids), Integer.toString(cpuMillisPerSecond)};
    }

    public static SandboxLimits parse(String[] args, int offset) {
        return new SandboxLimits(Long.parseLong(args[offset]), Long.parseLong(args[offset + 1]),
                Integer.parseInt(args[offset + 2]), Integer.parseInt(args[offset + 3]));
    }
}
