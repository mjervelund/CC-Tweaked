// SPDX-FileCopyrightText: 2021 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.computer.upload;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class FileUpload {
    private static final Logger LOG = LoggerFactory.getLogger(FileUpload.class);

    public static final int CHECKSUM_LENGTH = 32;

    private final String name;
    private final int length;
    private @Nullable ByteBuffer bytes;
    private final byte[] checksum;

    public FileUpload(String name, ByteBuffer bytes, byte[] checksum) {
        this.name = name;
        this.bytes = bytes;
        length = bytes.remaining();
        this.checksum = checksum;
    }

    /**
     * Create an upload whose contents are only allocated when first used. This is used when receiving uploads, so that
     * a packet can't make the server allocate a file before it has been checked.
     *
     * @param name     The file name.
     * @param length   The file length.
     * @param checksum The expected checksum.
     */
    public FileUpload(String name, int length, byte[] checksum) {
        this.name = name;
        this.length = length;
        this.checksum = checksum;
    }

    public String getName() {
        return name;
    }

    public ByteBuffer getBytes() {
        var bytes = this.bytes;
        if (bytes == null) bytes = this.bytes = ByteBuffer.allocate(length);
        return bytes;
    }

    public int getLength() {
        return length;
    }

    public byte[] getChecksum() {
        return checksum;
    }

    public boolean checksumMatches() {
        // This is meant to be a checksum. Doesn't need to be cryptographically secure, hence non-constant time.
        var digest = getDigest(getBytes());
        return digest != null && Arrays.equals(checksum, digest);
    }

    public static byte @Nullable [] getDigest(ByteBuffer bytes) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes.duplicate());
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            LOG.warn("Failed to compute digest ({})", e.toString());
            return null;
        }
    }
}
