package dev.filegate.formats.images;

import dev.filegate.core.CheckStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class RestrictedImageTest {
    @TempDir Path temp;
    private ImageProfile profile() { return new ImageProfile(1024 * 1024, 65536, 1024 * 1024, 4096, 128); }
    private CheckStatus inspect(byte[] bytes) throws Exception { return inspect(bytes, profile()); }
    private CheckStatus inspect(byte[] bytes, ImageProfile profile) throws Exception {
        Path path = temp.resolve("input");
        Files.write(path, bytes);
        return new RestrictedImageInspector(profile).inspect("image.restricted", path).status();
    }

    static byte[] encode(String format, boolean alpha, boolean progressive) throws Exception {
        var image = new BufferedImage(16, 16, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) image.setRGB(x, y, 0xff000000 | x * 10 << 16 | y * 10);
        var bytes = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName(format).next();
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            var params = writer.getDefaultWriteParam();
            if (progressive) params.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), params);
        } finally { writer.dispose(); image.flush(); }
        return bytes.toByteArray();
    }

    static byte[] chunk(String type, byte[] data) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var output = new DataOutputStream(bytes);
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        output.writeInt(data.length);
        output.write(name);
        output.write(data);
        var crc = new CRC32(); crc.update(name); crc.update(data);
        output.writeInt((int) crc.getValue());
        return bytes.toByteArray();
    }

    static byte[] png(byte[] raw, byte[] extraCompressed) throws Exception {
        var zipped = new ByteArrayOutputStream();
        try (var deflate = new DeflaterOutputStream(zipped)) { deflate.write(raw); }
        var bytes = new ByteArrayOutputStream();
        bytes.write(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        bytes.write(chunk("IHDR", ByteBuffer.allocate(13).putInt(1).putInt(1).put(new byte[] {8, 2, 0, 0, 0}).array()));
        var data = new ByteArrayOutputStream(); data.write(zipped.toByteArray()); data.write(extraCompressed);
        bytes.write(chunk("IDAT", data.toByteArray()));
        bytes.write(chunk("IEND", new byte[0]));
        return bytes.toByteArray();
    }

    @Test void supportedPngAndBaselineJpegDecodeCompletely() throws Exception {
        assertEquals(CheckStatus.PASS, inspect(encode("png", false, false)));
        assertEquals(CheckStatus.PASS, inspect(encode("png", true, false)));
        assertEquals(CheckStatus.PASS, inspect(encode("jpeg", false, false)));
        assertEquals(CheckStatus.PASS, inspect(png(new byte[] {0, 1, 2, 3}, new byte[0])));
    }

    @Test void pngCrcTruncationAndTrailingBytesReject() throws Exception {
        byte[] valid = encode("png", false, false);
        byte[] changed = valid.clone(); changed[29] ^= 1;
        assertEquals(CheckStatus.FAIL, inspect(changed));
        assertEquals(CheckStatus.FAIL, inspect(Arrays.copyOf(valid, valid.length - 1)));
        assertEquals(CheckStatus.FAIL, inspect(Arrays.copyOf(valid, valid.length + 1)));
    }

    @Test void pngDeflatePostconditionsRejectHiddenOrExcessData() throws Exception {
        assertEquals(CheckStatus.FAIL, inspect(png(new byte[] {0, 1, 2, 3}, new byte[] {42})));
        assertEquals(CheckStatus.FAIL, inspect(png(new byte[1024], new byte[0])));
        assertEquals(CheckStatus.FAIL, inspect(png(new byte[] {5, 1, 2, 3}, new byte[0])));
        assertEquals(CheckStatus.FAIL, inspect(png(new byte[] {0, 1}, new byte[0])));
    }

    @Test void unknownMetadataAndAnimationStructuresReject() throws Exception {
        byte[] valid = encode("png", false, false);
        for (String type : new String[] {"tEXt", "acTL", "eXIf", "PLTE"}) {
            var bytes = new ByteArrayOutputStream();
            bytes.write(valid, 0, 33);
            bytes.write(chunk(type, new byte[] {1}));
            bytes.write(valid, 33, valid.length - 33);
            assertEquals(CheckStatus.FAIL, inspect(bytes.toByteArray()));
        }
    }

    @Test void jpegProgressiveMetadataTruncationAndTrailingBytesReject() throws Exception {
        byte[] valid = encode("jpeg", false, false);
        assertEquals(CheckStatus.FAIL, inspect(encode("jpeg", false, true)));
        assertEquals(CheckStatus.FAIL, inspect(Arrays.copyOf(valid, valid.length - 2)));
        assertEquals(CheckStatus.FAIL, inspect(Arrays.copyOf(valid, valid.length + 1)));
        var bytes = new ByteArrayOutputStream();
        bytes.write(valid, 0, 2);
        bytes.write(new byte[] {(byte) 255, (byte) 254, 0, 4, 'h', 'i'});
        bytes.write(valid, 2, valid.length - 2);
        assertEquals(CheckStatus.FAIL, inspect(bytes.toByteArray()));
    }

    @Test void dimensionsInputMetadataAndSegmentEnvelopesAreExplicit() throws Exception {
        byte[] png = encode("png", false, false);
        byte[] jpeg = encode("jpeg", false, false);
        assertEquals(CheckStatus.FAIL, inspect(png, new ImageProfile(1, 65536, 1024 * 1024, 4096, 128)));
        assertEquals(CheckStatus.FAIL, inspect(png, new ImageProfile(1024 * 1024, 4, 1024 * 1024, 4096, 128)));
        assertEquals(CheckStatus.FAIL, inspect(png, new ImageProfile(1024 * 1024, 65536, 1024 * 1024, 4096, 2)));
        assertEquals(CheckStatus.FAIL, inspect(jpeg, new ImageProfile(1024 * 1024, 65536, 1024 * 1024, 0, 128)));
    }
}
