package com.demo.catalog.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class ImageTransformService {

    public byte[] createThumbnail(byte[] sourceBytes) {
        try {
            BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(sourceBytes));
            if (sourceImage == null) {
                throw new IllegalArgumentException("Unsupported image format");
            }

            int width = sourceImage.getWidth();
            int height = sourceImage.getHeight();
            int targetWidth = 300;
            int targetHeight = Math.max(1, (height * targetWidth) / Math.max(width, 1));

            BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = resized.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(sourceImage, 0, 0, targetWidth, targetHeight, null);
            graphics.dispose();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpg", outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create thumbnail", exception);
        }
    }
}
