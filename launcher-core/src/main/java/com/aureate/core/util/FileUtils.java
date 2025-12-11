package com.aureate.core.util;

import java.io.IOException;
import java.nio.file.*;

public class FileUtils {
    public static void moveAtomically(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static boolean existsAndMatches(Path path, String expectedSha256) {
        try {
            if (!Files.exists(path)) return false;
            String sha = SHA256Util.sha256(path);
            return sha.equalsIgnoreCase(expectedSha256);
        } catch (Exception ex) {
            return false;
        }
    }
}
