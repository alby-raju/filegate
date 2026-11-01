# Restricted image profile

The `filegate-format-images` artifact contains a restricted inspector and local
regression fixtures. It is not yet connected to the core through the Linux
runtime. Do not register its parser as an embedded check. The next implementation
slice must connect digest-bound worker input, repeat the containment probe and
exercise a complete inspection/release workflow inside that runtime.

## PNG coverage

The current profile accepts static, non-interlaced PNG with 8-bit RGB or RGBA,
standard compression and filter methods. It verifies the complete signature,
first/unique IHDR, supported chunk ordering, every chunk CRC, consecutive IDAT,
unique terminal IEND and absence of trailing bytes. Only sRGB and pHYs ancillary
chunks are supported, each once before IDAT with bounded lengths and selected
field checks. Palette images, grayscale, animation, text, EXIF and unknown chunks
are outside the supported subset and rejected.

IDAT is inflated with fixed buffers. The inspector bounds exact scanline output,
checks filter-byte values and requires complete zlib termination without trailing
compressed bytes. It then decodes through the JDK PNG reader and checks dimensions
and warnings. This follows the supported subset of the
[PNG chunk contract](https://www.w3.org/TR/png/); it does not claim universal PNG
support, polyglot absence, absence of hidden information or viewer safety.

## JPEG coverage

The profile accepts 8-bit baseline sequential grayscale or three-component JPEG
with one scan covering all frame components. It checks frame/scan component IDs,
dimensions, baseline quantization tables, bounded Huffman table structures,
restart markers when enabled, terminal EOI and absence of trailing bytes. JFIF
APP0 version 1.0–1.2 metadata is supported within the metadata allowance. Other APP
segments, comments, progressive/lossless encodings, CMYK and additional scans are
outside the subset and rejected. It then decodes with the JDK JPEG reader and
requires consistent dimensions and no decoder warnings.

The explicit reader selection uses the JDK PNG/JPEG provider identities;
unexpected third-party ImageIO providers are not substituted. The
[ImageReader API](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/imageio/ImageReader.html)
belongs to `java.desktop`, isolated from the `java.base` core artifact. The worker
image's JDK build must be pinned and inventoried before release.

## Limits and evidence

`ImageProfile` requires explicit input bytes, pixel count, decoded bytes, metadata
bytes and chunk/segment count limits. These values are deployment inputs, not
production recommendations. PNG inflation uses fixed buffers; JPEG segment buffers
are bounded by its 16-bit segment length. Full decoded pixel allocation belongs
inside the configured OS memory envelope; pixel checks alone are not an RSS limit.

Definite supported-profile violations and truncation reject. Unexpected parser
or I/O failures produce incomplete evidence. There is no antivirus, authenticity,
confidentiality-removal or arbitrary-viewer guarantee. Parser compromise remains
possible; successful local tests do not establish containment or release readiness.

Six regression tests cover generated RGB/RGBA PNG and baseline JPEG, PNG CRC and
zlib postconditions, truncation/trailing data, animation/unsupported metadata,
progressive JPEG and explicit resource ceilings. The broader adversarial corpus,
fuzzing and independent review remain roadmap gates. The explicit
[isolated adapter](isolated-images.md) binds the image digest and profile limits.
