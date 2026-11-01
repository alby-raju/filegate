package dev.filegate.worker;

import dev.filegate.core.CheckEvidence;

/** Transport fault fixture only. It does not inspect content. */
public final class WorkerStubMain {
    public static void main(String[] args) throws Exception {
        var request = WorkerProtocol.readRequest(System.in);
        switch (args[0]) {
            case "crash" -> System.exit(7);
            case "hang" -> Thread.sleep(60_000);
            case "stderr" -> {
                System.err.write(new byte[2048]);
                System.err.flush();
                Thread.sleep(60_000);
            }
            case "bad" -> {
                System.out.write(new byte[] {0, 0, 0, 0});
                System.out.flush();
            }
            default -> {
                WorkerProtocol.writeResponse(System.out, new WorkerResponse(request,
                        request.expectedChecks().stream().map(CheckEvidence::pass).toList()));
                if (args[0].equals("trailing")) { System.out.write(42); System.out.flush(); }
                if (args[0].equals("late-exit")) Thread.sleep(60_000);
            }
        }
    }
}
