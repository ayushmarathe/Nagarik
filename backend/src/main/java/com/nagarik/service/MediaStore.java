package com.nagarik.service;

import com.nagarik.domain.MediaKind;
import com.nagarik.error.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

/**
 * Where the bytes live.
 *
 * Three rules, and all three exist because an upload endpoint is the most
 * directly attacker-facing thing in the application:
 *
 * 1. The type is decided by looking at the first few bytes, never by trusting
 *    the Content-Type header or the file extension. Both of those are supplied
 *    by the caller, so a .png that is actually an HTML document would otherwise
 *    be stored as an image and later served back with Content-Type: text/html,
 *    which is a stored cross-site-scripting hole on our own origin.
 *
 * 2. The name on disk is generated here. A caller-supplied filename is an
 *    instruction about where to write - "../../../application.properties" is a
 *    perfectly ordinary string until it reaches resolve(). The original name is
 *    kept in the database for display and never touches the filesystem.
 *
 * 3. Size is capped per kind, and the cap is enforced here as well as by
 *    Spring's multipart limit. The multipart limit rejects the request before a
 *    controller runs, which gives a worse message; this one gives a specific
 *    one. Belt and braces, with the outer limit as the real protection.
 */
@Service
public class MediaStore {

    /** Cap per file. Must stay at or under spring.servlet.multipart.max-file-size. */
    public static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    public static final long MAX_VIDEO_BYTES = 40L * 1024 * 1024;

    /** Enough to hold a problem without turning a report into an album. */
    public static final int MAX_PER_ISSUE = 6;

    private final Path root;

    public MediaStore(@Value("${nagarik.media.root:./media}") String rootPath) {
        this.root = Paths.get(rootPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            // Failing at startup with the path in the message beats failing on
            // the first upload with a stack trace nobody can act on.
            throw new IllegalStateException(
                    "Could not create the media directory at " + root
                            + ". Set nagarik.media.root to somewhere writable.", e);
        }
    }

    public Path getRoot() {
        return root;
    }

    /** What the bytes actually are, as opposed to what the upload said. */
    public record Sniffed(MediaKind kind, String contentType, String extension) {
    }

    /**
     * Reads the magic number. Returns null when the bytes are not one of the
     * handful of formats a browser can display without a plugin, which is the
     * only set worth accepting - anything else would be stored and then be
     * undisplayable.
     */
    public Sniffed sniff(byte[] bytes) {
        if (bytes.length < 12) {
            return null;
        }

        if (starts(bytes, 0xFF, 0xD8, 0xFF)) {
            return new Sniffed(MediaKind.IMAGE, "image/jpeg", ".jpg");
        }
        if (starts(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return new Sniffed(MediaKind.IMAGE, "image/png", ".png");
        }
        if (ascii(bytes, 0, "GIF8")) {
            return new Sniffed(MediaKind.IMAGE, "image/gif", ".gif");
        }
        // WebP is a RIFF container: "RIFF" then four bytes of length then "WEBP".
        if (ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) {
            return new Sniffed(MediaKind.IMAGE, "image/webp", ".webp");
        }
        // ISO base media (MP4 and friends) puts the box type at offset 4.
        if (ascii(bytes, 4, "ftyp")) {
            return new Sniffed(MediaKind.VIDEO, "video/mp4", ".mp4");
        }
        // Matroska/WebM: EBML header.
        if (starts(bytes, 0x1A, 0x45, 0xDF, 0xA3)) {
            return new Sniffed(MediaKind.VIDEO, "video/webm", ".webm");
        }
        return null;
    }

    private static boolean starts(byte[] bytes, int... expected) {
        if (bytes.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i += 1) {
            if ((bytes[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        byte[] want = expected.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < offset + want.length) {
            return false;
        }
        for (int i = 0; i < want.length; i += 1) {
            if (bytes[offset + i] != want[i]) {
                return false;
            }
        }
        return true;
    }

    /** The cap that applies to this kind of file. */
    public long capFor(MediaKind kind) {
        return kind == MediaKind.VIDEO ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
    }

    /**
     * Writes the bytes under a generated name and returns that name.
     *
     * @return the name to store on the attachment row; the only name that is
     *         ever joined onto the media root
     */
    public String write(byte[] bytes, Sniffed sniffed) {
        String storedName = UUID.randomUUID() + sniffed.extension();
        Path target = resolveInsideRoot(storedName);
        try {
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the upload to " + target, e);
        }
        return storedName;
    }

    public Resource read(String storedName) {
        return new FileSystemResource(resolveInsideRoot(storedName));
    }

    public long sizeOnDisk(String storedName) {
        try {
            return Files.size(resolveInsideRoot(storedName));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not stat " + storedName, e);
        }
    }

    public boolean exists(String storedName) {
        return Files.isRegularFile(resolveInsideRoot(storedName));
    }

    /** Best effort: a row removed with its file already gone is still removed. */
    public void delete(String storedName) {
        try {
            Files.deleteIfExists(resolveInsideRoot(storedName));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete " + storedName, e);
        }
    }

    /**
     * Joins a name onto the root and refuses anything that escapes it.
     *
     * Every name reaching here is one this class generated, so the check should
     * never fire. It stays because the day someone adds a path that comes from
     * a request, this is the line that makes that safe instead of catastrophic.
     */
    private Path resolveInsideRoot(String storedName) {
        Path target = root.resolve(storedName).normalize();
        if (!target.startsWith(root)) {
            throw new BadRequestException("That file name is not allowed");
        }
        return target;
    }

    /**
     * Pixel size, for laying the gallery out before the image arrives.
     *
     * ImageIO ships with the JDK and reads JPEG, PNG and GIF; it does not read
     * WebP, so those come back null. Null is a normal answer here, not a
     * failure - the interface falls back to a square placeholder.
     */
    public int[] imageDimensions(byte[] bytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                return null;
            }
            return new int[] { image.getWidth(), image.getHeight() };
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * A filename safe to put in a Content-Disposition header.
     *
     * Quotes and control characters in that header let a caller add header
     * fields of their own, so everything outside a conservative set is dropped
     * rather than escaped.
     */
    public static String headerSafeName(String original) {
        String cleaned = original == null ? "" : original.trim().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(cleaned.length());
        for (int i = 0; i < cleaned.length(); i += 1) {
            char c = cleaned.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-' || c == '_';
            out.append(ok ? c : '-');
        }
        String result = out.toString();
        return result.isBlank() ? "attachment" : result;
    }
}
