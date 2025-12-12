package com.aureate.client;

import com.aureate.core.model.BuildManifest;

import java.nio.file.Path;
import java.nio.file.Paths;

public class LauncherUtils {

    public static void launchMinecraft(BuildManifest manifest) throws Exception {
        Path javaPath = Paths.get(System.getProperty("java.home"), "bin", "java.exe");
        Path instanceDir = Paths.get("instances", manifest.getBuildId(), "client.jar");

        ProcessBuilder pb = new ProcessBuilder(
                javaPath.toString(),
                "-Xmx2G",
                "-jar",
                instanceDir.toAbsolutePath().toString()
        );
        pb.inheritIO();
        pb.start();
    }
}
