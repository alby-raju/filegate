package dev.filegate.core;

/** Explicitly trusted embedded check. Deep hostile-file parsers require isolated workers. */
public interface InspectionCheck {
    CheckDescriptor descriptor();

    /** Input access is revoked when this call returns; do not retain it or start background tasks. */
    CheckEvidence inspect(InspectionInput input) throws Exception;
}
