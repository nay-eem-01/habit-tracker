package com.nayeem.habittracker.user;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarImageTest {

    @Test
    void makesTheCentreSquareAtMost256PxAsJpeg() throws IOException {
        // left and right thirds red, centre blue: the crop keeps the blue
        BufferedImage wide = new BufferedImage(1200, 400, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 1200; x++) {
            for (int y = 0; y < 400; y++) {
                wide.setRGB(x, y, x >= 400 && x < 800 ? Color.BLUE.getRGB() : Color.RED.getRGB());
            }
        }

        byte[] jpeg = AvatarImage.normalize(encode(wide, "png"));

        assertThat(jpeg).startsWith(0xFF, 0xD8); // JPEG magic
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(out.getWidth()).isEqualTo(256);
        assertThat(out.getHeight()).isEqualTo(256);
        assertThat(new Color(out.getRGB(128, 128)).getBlue()).isGreaterThan(200);
        assertThat(new Color(out.getRGB(5, 128)).getRed()).isLessThan(60);
    }

    @Test
    void neverScalesASmallPictureUp() throws IOException {
        byte[] jpeg = AvatarImage.normalize(encode(new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB), "jpeg"));
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(out.getWidth()).isEqualTo(80);
        assertThat(out.getHeight()).isEqualTo(80);
    }

    @Test
    void fillsTransparencyWithWhite() throws IOException {
        byte[] jpeg = AvatarImage.normalize(encode(new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB), "png"));
        Color pixel = new Color(ImageIO.read(new ByteArrayInputStream(jpeg)).getRGB(25, 25));
        assertThat(pixel.getRed() + pixel.getGreen() + pixel.getBlue()).isGreaterThan(740);
    }

    @Test
    void refusesWhatIsNotAPicture() {
        assertThatThrownBy(() -> AvatarImage.normalize("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_AVATAR_INVALID));
        assertThatThrownBy(() -> AvatarImage.normalize(new byte[0]))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_AVATAR_INVALID));
    }

    @Test
    void refusesOver5Mb() {
        assertThatThrownBy(() -> AvatarImage.normalize(new byte[AvatarImage.MAX_BYTES + 1]))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_AVATAR_TOO_LARGE));
    }

    static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, format, bytes);
        return bytes.toByteArray();
    }
}
