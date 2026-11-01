package dev.filegate.examples;

import dev.filegate.core.ApprovedContent;

import java.io.IOException;
import java.nio.file.Path;

/** The host owns destination finalization and visibility. It must authorize publication first. */
public final class PublicationExample {
    private PublicationExample() {}

    public static Path publish(ApprovedContent content, Path storageParent) throws IOException {
        try (var pending = new LocalPendingObject(storageParent, content.use())) {
            var receipt = content.copyTo(pending.output(), pending.id(), pending.use());
            pending.finishWrites();
            return pending.commit(receipt);
        }
    }
}
