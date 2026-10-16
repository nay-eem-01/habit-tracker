package com.nayeem.habittracker.user;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Turns an uploaded picture into the stored avatar: the centre square, at most {@value #SIZE} px, as a
 * JPEG. Re-encoding means only real pixels are kept — no metadata (a photo's location), nothing a
 * browser could run. Plain class, no Spring, so it is unit-tested directly.
 */
final class AvatarImage {

    static final int SIZE = 256;
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final float QUALITY = 0.85f;

    private AvatarImage() {
    }

    /**
     * @throws ApplicationException {@code USER_AVATAR_TOO_LARGE} over {@value #MAX_BYTES} bytes;
     *                              {@code USER_AVATAR_INVALID} when it isn't a picture ImageIO can read
     *                              (JPEG, PNG, GIF, BMP — WebP isn't one)
     */
    static byte[] normalize(byte[] upload) {
        if (upload.length > MAX_BYTES) {
            throw new ApplicationException(ErrorCode.USER_AVATAR_TOO_LARGE);
        }
        BufferedImage source;
        try {
            source = read(upload);
        } catch (IOException | RuntimeException e) {
            throw new ApplicationException(ErrorCode.USER_AVATAR_INVALID);
        }
        if (source == null) {
            throw new ApplicationException(ErrorCode.USER_AVATAR_INVALID);
        }
        return toJpeg(square(source));
    }

    /**
     * Decodes with subsampling, so a huge picture (or one claiming to be huge) is read at about twice
     * the final size and never fills the heap.
     */
    private static BufferedImage read(byte[] upload) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int shortSide = Math.min(reader.getWidth(0), reader.getHeight(0));
                int step = Math.max(1, shortSide / (2 * SIZE));
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
    }

    /** The centre square, scaled down to {@value #SIZE} px (never up), on white where it was transparent. */
    private static BufferedImage square(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int target = Math.min(side, SIZE);
        BufferedImage out = new BufferedImage(target, target, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, target, target);
            int x = (source.getWidth() - side) / 2;
            int y = (source.getHeight() - side) / 2;
            g.drawImage(source, 0, 0, target, target, x, y, x + side, y + side, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] toJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode the avatar", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
