package com.aureate.client;

import com.aureate.core.LauncherPaths;
import com.aureate.core.model.BuildManifest;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class LauncherUtils {

    public static void launchMinecraft(BuildManifest manifest) throws Exception {
        Path baseDir = LauncherPaths.getBaseDir();
        Path clientJar = LauncherPaths.getInstanceDir(manifest.getBuildId()).resolve("client.jar");

        // Build classpath
        List<String> classpath = new ArrayList<>();
        classpath.add(clientJar.toAbsolutePath().toString());
        // Add all jars from libraries directory (more robust than trusting manifest paths)
        Path libsRoot = baseDir.resolve("libraries");
        if (Files.exists(libsRoot)) {
            try (java.util.stream.Stream<Path> s = java.nio.file.Files.walk(libsRoot)) {
                s.filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                 .forEach(p -> classpath.add(p.toAbsolutePath().toString()));
            }
        } else {
            System.out.println("Libraries folder not found: " + libsRoot);
        }

        // Prepare per-instance natives directory (versions/<buildId>/natives)
        Path instanceDir = LauncherPaths.getInstanceDir(manifest.getBuildId());
        Path nativesDir = instanceDir.resolve("natives");
        try {
            if (Files.exists(nativesDir)) {
                try (java.util.stream.Stream<Path> walk = Files.walk(nativesDir)) {
                    walk.sorted(java.util.Comparator.<Path>reverseOrder()).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
                }
            }
            Files.createDirectories(nativesDir);
        } catch (Exception ex) {
            System.out.println("Failed to prepare natives dir: " + ex.getMessage());
        }

        // Identify current OS/arch to select appropriate native classifiers
        String osName = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        boolean isWindows = osName.contains("win");
        boolean isMac = osName.contains("mac") || osName.contains("darwin") || osName.contains("osx");
        boolean isLinux = osName.contains("nux") || osName.contains("nix");

        // Helper: build maven-style path including classifier
        java.util.function.BiFunction<String, String, String> buildClassifierPath = (libName, classifier) -> {
            String[] parts = libName.split(":");
            if (parts.length < 3) return null;
            String groupPath = parts[0].replace('.', '/');
            String artifact = parts[1];
            String version = parts[2];
            return groupPath + "/" + artifact + "/" + version + "/" + artifact + "-" + version + (classifier == null || classifier.isEmpty() ? "" : ("-" + classifier)) + ".jar";
        };

        // Extract native classifier jars referenced in manifest libraries
        if (manifest.getLibraries() != null && Files.exists(libsRoot)) {
            for (BuildManifest.Library lib : manifest.getLibraries()) {
                try {
                    BuildManifest.Downloads dl = lib.getDownloads();
                    if (dl == null) continue;
                    java.util.Map<String, BuildManifest.Artifact> classifiers = dl.getClassifiers();
                    if (classifiers == null || classifiers.isEmpty()) continue;

                    // find a classifier key that looks like natives for this OS
                    String chosen = null;
                    for (String key : classifiers.keySet()) {
                        String lower = key.toLowerCase();
                        if (!lower.contains("natives")) continue;
                        if (isWindows && lower.contains("win")) { chosen = key; break; }
                        if (isMac && (lower.contains("osx") || lower.contains("mac"))) { chosen = key; break; }
                        if (isLinux && lower.contains("linux")) { chosen = key; break; }
                        // fallbacks
                        if (chosen == null) chosen = key;
                    }
                    if (chosen == null) continue;

                    String rel = buildClassifierPath.apply(lib.getName(), chosen);
                    if (rel == null) continue;
                    Path jarPath = libsRoot.resolve(rel);
                    if (!Files.exists(jarPath)) {
                        // try artifact name without classifier (some manifests put natives in artifact classifier path differently)
                        Path alt = libsRoot.resolve(buildClassifierPath.apply(lib.getName(), null));
                        if (Files.exists(alt)) jarPath = alt; else continue;
                    }

                    // extract entries (skip META-INF)
                    try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jarPath.toFile())) {
                        java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
                        while (entries.hasMoreElements()) {
                            java.util.zip.ZipEntry entry = entries.nextElement();
                            if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) continue;
                            String name = entry.getName();
                            String lower = name.toLowerCase();
                            if (!(lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(".dylib") || lower.endsWith(".jnilib") || lower.endsWith(".lib"))) continue;
                            Path out = nativesDir.resolve(java.nio.file.Paths.get(name).getFileName());
                            Files.createDirectories(out.getParent());
                            try (java.io.InputStream is = zip.getInputStream(entry); java.io.OutputStream os = Files.newOutputStream(out, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                                byte[] buf = new byte[8192]; int r;
                                while ((r = is.read(buf)) != -1) os.write(buf, 0, r);
                            }
                        }
                    }
                } catch (Exception ex) {
                    System.out.println("Failed extracting natives for library " + lib.getName() + ": " + ex.getMessage());
                }
            }
        }

        // Fallback: if nativesDir is empty, search any jar under libraries that has 'natives' in the filename and try to extract from it
        try {
            boolean anyNatives = Files.exists(nativesDir) && Files.list(nativesDir).findAny().isPresent();
            if (!anyNatives && Files.exists(libsRoot)) {
                try (java.util.stream.Stream<Path> s = Files.walk(libsRoot)) {
                    s.filter(p -> p.toString().toLowerCase().endsWith(".jar") && p.getFileName().toString().toLowerCase().contains("natives"))
                     .forEach(p -> {
                         try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(p.toFile())) {
                             java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
                             while (entries.hasMoreElements()) {
                                 java.util.zip.ZipEntry entry = entries.nextElement();
                                 if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) continue;
                                 String name = entry.getName();
                                 String lower = name.toLowerCase();
                                 if (!(lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(".dylib") || lower.endsWith(".jnilib") || lower.endsWith(".lib"))) continue;
                                 Path out = nativesDir.resolve(java.nio.file.Paths.get(name).getFileName());
                                 Files.createDirectories(out.getParent());
                                 try (java.io.InputStream is = zip.getInputStream(entry); java.io.OutputStream os = Files.newOutputStream(out, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                                     byte[] buf = new byte[8192]; int r;
                                     while ((r = is.read(buf)) != -1) os.write(buf, 0, r);
                                 }
                             }
                         } catch (Exception ex) {
                             System.out.println("Fallback extract failed from " + p + ": " + ex.getMessage());
                         }
                     });
                }
            }
        } catch (Exception ex) {
            System.out.println("Error during fallback native scan: " + ex.getMessage());
        }

        // Log nativesDir contents for debugging
        try {
            System.out.println("Natives dir contents:");
            if (Files.exists(nativesDir)) {
                try (java.util.stream.Stream<Path> walk = Files.walk(nativesDir)) {
                    walk.filter(Files::isRegularFile).forEach(p -> System.out.println("  " + p.toAbsolutePath()));
                }
            } else {
                System.out.println("  (natives dir does not exist)");
            }
        } catch (Exception ex) {
            System.out.println("Failed listing natives dir: " + ex.getMessage());
        }

        // Log any libraries referenced in manifest that are missing on disk (helpful for debugging)
        if (manifest.getLibraries() != null) {
            for (BuildManifest.Library lib : manifest.getLibraries()) {
                try {
                    String expected = libsRoot.resolve(buildMavenPath(lib.getName())).toAbsolutePath().toString();
                    if (!Files.exists(java.nio.file.Paths.get(expected))) {
                        System.out.println("Missing library file referenced by manifest: " + expected);
                    }
                } catch (Exception ignored) {}
            }
        }

        String cp = String.join(System.getProperty("path.separator"), classpath);
        System.out.println("Classpath (" + classpath.size() + " entries):");
        for (String p : classpath) System.out.println("  " + p);

        List<String> command = new ArrayList<>();
        command.add(System.getProperty("java.home") + "/bin/java.exe");

        // Memory settings from manifest if available
        if (manifest.getJava() != null) {
            int min = manifest.getJava().getMinRamMb();
            int max = manifest.getJava().getMaxRamMb();
            if (min > 0) command.add("-Xms" + min + "M");
            if (max > 0) command.add("-Xmx" + max + "M");
        } else {
            command.add("-Xmx2G");
        }

        // Recommended JVM flags used by many launchers
        command.add("-XX:+UseG1GC");
        command.add("-XX:+DisableExplicitGC");
        command.add("-Djava.library.path=" + nativesDir.toAbsolutePath().toString());
        command.add("-cp");
        command.add(cp);
        command.add(manifest.getMainClass() != null ? manifest.getMainClass() : "net.minecraft.client.main.Main");
        command.add("--username");
        command.add("Player");
        command.add("--version");
        command.add(manifest.getMcVersion() != null ? manifest.getMcVersion() : manifest.getBuildId());
        command.add("--gameDir");
        command.add(baseDir.toAbsolutePath().toString());
        command.add("--assetsDir");
        command.add(baseDir.resolve("assets").toAbsolutePath().toString());
        command.add("--assetsIndex");
        command.add(manifest.getAssetIndex() != null ? manifest.getAssetIndex().getId() : manifest.getMcVersion());
        command.add("--accessToken");
        command.add("");
        command.add("--uuid");
        command.add("00000000-0000-0000-0000-000000000000");
        command.add("--userType");
        command.add("legacy");
        command.add("--versionType");
        command.add("release");

        ProcessBuilder pb = new ProcessBuilder(command);
        System.out.println("Launching JVM with command:");
        for (String c : command) System.out.print(c + " ");
        System.out.println();
        pb.directory(baseDir.toFile());
        pb.inheritIO();
        pb.environment().put("LWJGL_DISABLE_OPENGL_CHECK", "true");
        pb.start();
    }

    private static boolean isApplicable(BuildManifest.Library lib) {
        return true;
    }

    private static String buildMavenPath(String name) {
        String[] parts = name.split(":");
        return parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2] + ".jar";
    }
}
