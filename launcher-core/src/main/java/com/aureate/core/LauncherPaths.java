package com.aureate.core;

import java.nio.file.Path;
import java.nio.file.Paths;

public class LauncherPaths {
    public static Path getBaseDir() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".aureate");
    }

    public static Path getInstancesDir() {
        return getBaseDir().resolve("instances");
    }

    public static Path getInstanceDir(String buildId) {
        return getInstancesDir().resolve(buildId);
    }

    public static Path getTempDir() {
        return getBaseDir().resolve("tmp");
    }
}
