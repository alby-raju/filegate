/*
 * FileGate
 * Policy-based attachment inspection and verified release.
 *
 * Copyright 2026 alby-raju
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.filegate.examples;

import dev.filegate.core.TransferReceipt;
import dev.filegate.core.Use;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * POSIX host example, not a universal storage adapter. A hard link publishes an
 * immutable name with atomic no-replace semantics on the same filesystem.
 * Parent directory durability and crash reconciliation remain host obligations.
 */
public final class LocalPendingObject implements AutoCloseable {
    private enum State { WRITING, FINISHED, FAILED, COMMITTED, CLOSED }
    private final String id = UUID.randomUUID().toString();
    private final Use use;
    private final Path directory;
    private final Path pending;
    private final Path object;
    private final FileChannel channel;
    private final OutputStream output;
    private State state = State.WRITING;

    public LocalPendingObject(Path storageParent, Use use) throws IOException {
        this.use = Objects.requireNonNull(use, "use");
        directory = Files.createTempDirectory(storageParent, ".pending-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        pending = directory.resolve("content");
        object = storageParent.resolve("object-" + id);
        try {
            channel = FileChannel.open(pending, java.util.Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            output = Channels.newOutputStream(channel);
        } catch (IOException | RuntimeException failure) {
            try { Files.deleteIfExists(pending); Files.deleteIfExists(directory); }
            catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public String id() { return id; }
    public Use use() { return use; }
    /** Stable reconciliation identifier. A path existing after an ambiguous outcome requires verification. */
    public Path objectPath() { return object; }
    public OutputStream output() {
        require(State.WRITING);
        return output;
    }

    public void finishWrites() throws IOException {
        require(State.WRITING);
        state = State.FAILED;
        try {
            channel.force(true);
        } finally {
            output.close();
        }
        Files.setPosixFilePermissions(pending, PosixFilePermissions.fromString("r--------"));
        state = State.FINISHED;
    }

    public Path commit(TransferReceipt receipt) throws IOException {
        require(State.FINISHED);
        state = State.FAILED;
        receipt.validateFor(id, use);
        verifyPersisted(receipt);
        // Atomic create-without-replace. Unsupported hard links fail; no weaker fallback.
        Files.createLink(object, pending);
        state = State.COMMITTED;
        return object;
    }

    private void verifyPersisted(TransferReceipt receipt) throws IOException {
        if (!Files.isRegularFile(pending, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid pending object");
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        long count = 0;
        byte[] buffer = new byte[8192];
        try (var input = Files.newInputStream(pending)) {
            for (int read; (read = input.read(buffer)) != -1;) {
                if (read > receipt.artifact().length() - count) throw new IOException("Persisted length mismatch");
                count += read;
                digest.update(buffer, 0, read);
            }
        }
        if (count != receipt.artifact().length()
                || !HexFormat.of().formatHex(digest.digest()).equals(receipt.artifact().sha256())) {
            throw new IOException("Persisted identity mismatch");
        }
    }

    private void require(State expected) {
        if (state != expected) throw new IllegalStateException("Invalid pending-object lifecycle");
    }

    @Override public void close() throws IOException {
        // Retrying close also retries failed deletion. Published hard links are never deleted here.
        state = State.CLOSED;
        try {
            output.close();
        } finally {
            try { Files.deleteIfExists(pending); }
            finally { Files.deleteIfExists(directory); }
        }
    }
}
