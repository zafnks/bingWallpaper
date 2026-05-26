package com.bingwallpaper;

import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches the Bing daily wallpaper, applies text overlay, and saves to disk. */
public class BingFetcher {
    private static final Logger LOG = Logger.getLogger(BingFetcher.class.getName());
    private static final String API_URL = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-CN";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /** Download today's wallpaper, overlay copyright text, and return the saved path. */
    public Path downloadLatestWallpaper(Path wallpaperDir, String resolution) throws IOException {
        if (!Files.exists(wallpaperDir)) {
            Files.createDirectories(wallpaperDir);
        }

        String[] imageInfo = fetchImageInfo();
        String imageUrl = imageInfo[0];
        String description = imageInfo[1];

        if (imageUrl == null) {
            throw new IOException("Failed to get image URL from Bing API");
        }

        String targetUrl = applyResolution(imageUrl, resolution);
        LOG.info("Downloading: " + targetUrl);

        String fileName = "bing_wallpaper_" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".jpg";
        Path tempPath = wallpaperDir.resolve(fileName + ".tmp");
        Path outputPath = wallpaperDir.resolve(fileName);

        downloadImage(targetUrl, tempPath);

        // Overlay description text onto the image
        if (description != null && !description.isEmpty()) {
            try {
                addTextOverlay(tempPath, outputPath, description);
                Files.deleteIfExists(tempPath);
                LOG.info("Overlaid description: " + description);
                return outputPath;
            } catch (Exception e) {
                LOG.warning("Text overlay failed, using original: " + e.getMessage());
            }
        }

        Files.move(tempPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
        return outputPath;
    }

    /** Parse Bing JSON API response, returning [imageUrl, description]. */
    private String[] fetchImageInfo() throws IOException {
        HttpURLConnection conn = null;
        try {
            URL apiEndpoint = new URL(API_URL);
            conn = (HttpURLConnection) apiEndpoint.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("API returned HTTP " + responseCode);
            }

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }

            String json = body.toString();

            Pattern urlPattern = Pattern.compile("\"url\"\\s*:\\s*\"(/th\\?id=OHR\\.[^\"]+)\"");
            Matcher urlMatcher = urlPattern.matcher(json);
            String imageUrl = null;
            if (urlMatcher.find()) {
                imageUrl = "https://www.bing.com" + urlMatcher.group(1).replace("\\/", "/");
            }

            Pattern copyrightPattern = Pattern.compile("\"copyright\"\\s*:\\s*\"([^\"]+)\"");
            Matcher copyrightMatcher = copyrightPattern.matcher(json);
            String description = copyrightMatcher.find() ? copyrightMatcher.group(1) : null;
            if (description != null) {
                description = description.replaceAll("\\s*[\\(（][^\\)）]*[\\)）]\\s*$", "").trim();
                // Reverse place name order from Bing's small→large to Chinese convention large→small
                description = description.replaceAll("，",",");
                String[] parts = description.split(",");
                if (parts.length > 1) {
                    StringBuilder reversed = new StringBuilder();
                    for (int i = parts.length - 1; i >= 0; i--) {
                        if (reversed.length() > 0) reversed.append(", ");
                        reversed.append(parts[i].trim());
                    }
                    description = reversed.toString();
                }
            }

            return new String[]{imageUrl, description};
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Render copyright description as a frosted-glass panel on the image. */
    private void addTextOverlay(Path srcPath, Path destPath, String text) throws IOException {
        BufferedImage image = ImageIO.read(srcPath.toFile());
        if (image == null) {
            throw new IOException("Failed to read image: " + srcPath);
        }

        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int fontSize = Math.max(12, image.getWidth() / 120);
            Font font = new Font("Microsoft YaHei", Font.BOLD, fontSize);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();

            int padding = fontSize;
            int margin = fontSize;
            int maxWidth = image.getWidth() / 2;

            List<String> lines = wrapText(text, fm, maxWidth);

            int lineHeight = fm.getHeight();
            int totalTextHeight = lines.size() * lineHeight;
            int maxLineWidth = 0;
            for (String line : lines) {
                int w = fm.stringWidth(line);
                if (w > maxLineWidth) maxLineWidth = w;
            }

            int rectWidth = Math.min(maxLineWidth + padding * 2, image.getWidth() - margin * 2);
            int rectHeight = Math.min(totalTextHeight + padding * 2, image.getHeight() - margin * 2);
            int rectX = image.getWidth() - rectWidth - margin;
            int rectY = margin;

            // Extract background region for frosted glass effect
            BufferedImage bgPart = new BufferedImage(rectWidth, rectHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D bgG = bgPart.createGraphics();
            bgG.drawImage(image, 0, 0, rectWidth, rectHeight,
                    rectX, rectY, rectX + rectWidth, rectY + rectHeight, null);
            bgG.dispose();

            // Frosted glass: blur the background then overlay semi-transparent gray
            BufferedImage blurred = fastBlur(bgPart, 6);
            g.drawImage(blurred, rectX, rectY, null);

            g.setColor(new Color(60, 60, 60, 140));
            g.fillRoundRect(rectX, rectY, rectWidth, rectHeight, fontSize / 2, fontSize / 2);

            // Draw text
            g.setColor(Color.WHITE);
            int textX = rectX + padding;
            int textY = rectY + padding + fm.getAscent();
            for (String line : lines) {
                g.drawString(line, textX, textY);
                textY += lineHeight;
            }
        } finally {
            g.dispose();
        }

        writeJpeg(image, destPath);
    }

    /** Fast approximate blur via downscale→upscale to simulate frosted glass. */
    private BufferedImage fastBlur(BufferedImage src, int radius) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 1 || h <= 1) return src;

        int sw = Math.max(1, w / (radius * 2));
        int sh = Math.max(1, h / (radius * 2));

        BufferedImage small = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = small.createGraphics();
        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        sg.drawImage(src, 0, 0, sw, sh, null);
        sg.dispose();

        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D rg = result.createGraphics();
        rg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        rg.drawImage(small, 0, 0, w, h, null);
        rg.dispose();

        return result;
    }

    /** Wrap text into lines fitting within maxWidth pixels. */
    private List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        if (fm.stringWidth(text) <= maxWidth) {
            lines.add(text);
            return lines;
        }

        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String next = current.toString() + c;
            if (fm.stringWidth(next) > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current = new StringBuilder(String.valueOf(c));
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }

        return lines;
    }

    /** Write a BufferedImage as a high-quality JPEG. */
    private void writeJpeg(BufferedImage image, Path outputPath) throws IOException {
        ImageWriter writer = null;
        try {
            writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.95f);

            try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputPath.toFile())) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            }
        } finally {
            if (writer != null) writer.dispose();
        }
    }

    /** Map resolution setting to Bing URL parameters. */
    private String applyResolution(String url, String resolution) {
        String res;
        switch (resolution.toLowerCase()) {
            case "1080p":
                res = "1920x1080";
                break;
            case "1200p":
                res = "1920x1200";
                break;
            case "4k":
            default:
                return upgradeToUhd(url);
        }
        return url.replaceAll("_UHD(\\.jpg)", "_" + res + "$1")
                  .replaceAll("_\\d+x\\d+(\\.jpg)", "_" + res + "$1")
                  .replaceAll("_UHD&rf", "_" + res + "&rf")
                  .replaceAll("_\\d+x\\d+&rf", "_" + res + "&rf");
    }

    /** Rewrite a non-UHD URL to request the UHD variant. */
    private String upgradeToUhd(String url) {
        String uhd = url.replaceAll("_\\d+x\\d+\\.jpg", "_UHD.jpg")
                        .replaceAll("_\\d+x\\d+&rf", "_UHD&rf");
        if (!uhd.contains("_UHD")) {
            uhd = uhd.replaceAll("\\.jpg", "_UHD.jpg");
        }
        return uhd;
    }

    /** Download raw image bytes via HTTP GET. */
    private void downloadImage(String imageUrl, Path outputPath) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(imageUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "image/webp,image/jpeg,image/*,*/*");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("HTTP " + responseCode + " downloading image");
            }

            try (InputStream is = conn.getInputStream()) {
                Files.copy(is, outputPath, StandardCopyOption.REPLACE_EXISTING);
            }
            LOG.info("Saved to: " + outputPath);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
