package com.aureate.core.updater;

import com.aureate.core.LauncherPaths;
import com.aureate.core.http.HttpClientHelper;
import com.aureate.core.model.BuildManifest;
import com.aureate.core.model.FileEntry;
import com.aureate.core.util.FileUtils;
import com.aureate.core.util.SHA256Util;
import com.google.gson.Gson;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class Downloader {
    public interface ProgressListener {
        void onFileProgress(FileEntry entry, long downloaded, long total);
        void onFileCompleted(FileEntry entry);
        void onFileFailed(FileEntry entry, Exception ex);
        default void onOverallProgress(long downloaded, long total) {}
    }

    private final ExecutorService executor;
    private final int parallel;
    private final Gson gson = new Gson();

    public Downloader(int parallel) {
        this.parallel = Math.max(1, parallel);
        this.executor = Executors.newFixedThreadPool(this.parallel);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public void installManifest(BuildManifest manifest, ProgressListener listener) throws InterruptedException, Exception {
        System.out.println("Installing manifest for id: " + manifest.id + ", buildId: " + manifest.getBuildId());
        List<Callable<Void>> tasks = new ArrayList<>();
        Path instanceBase = LauncherPaths.getInstanceDir(manifest.id);
        System.out.println("Instance dir: " + instanceBase);
        Path tmp = LauncherPaths.getTempDir();
        try {
            Files.createDirectories(instanceBase);
            Files.createDirectories(tmp);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create dirs", e);
        }

        // Prepare overall progress counters
        long initialTotal = 0L;
        if (manifest.getFiles() != null) {
            for (FileEntry fe : manifest.getFiles()) {
                initialTotal += fe.getSize();
            }
        }
        if (manifest.getDownloads() != null && manifest.getDownloads().getClient() != null) {
            initialTotal += manifest.getDownloads().getClient().getSize();
        }
        if (manifest.getAssetIndex() != null) {
            initialTotal += manifest.getAssetIndex().getSize();
        }
        if (manifest.getLibraries() != null) {
            for (BuildManifest.Library lib : manifest.getLibraries()) {
                try {
                    BuildManifest.Artifact art = lib.getDownloads().getArtifact();
                    if (art != null) initialTotal += art.getSize();
                } catch (Exception ignored) {}
            }
        }

        AtomicLong totalBytes = new AtomicLong(initialTotal);
        AtomicLong downloadedBytes = new AtomicLong(0L);

        // Download files
        if (manifest.getFiles() != null) {
            for (FileEntry fe : manifest.getFiles()) {
                tasks.add(() -> {
                    downloadFile(fe, instanceBase, tmp, listener, downloadedBytes, totalBytes);
                    return null;
                });
            }
        }

        // Download client jar
        if (manifest.getDownloads() != null && manifest.getDownloads().getClient() != null) {
            BuildManifest.Artifact clientArtifact = manifest.getDownloads().getClient();
            FileEntry clientFe = new FileEntry();
            clientFe.setPath("client.jar");
            clientFe.setUrl(clientArtifact.getUrl());
            clientFe.setSha256(clientArtifact.getSha1());
            clientFe.setSize(clientArtifact.getSize());
            tasks.add(() -> {
                downloadFile(clientFe, instanceBase, tmp, listener, downloadedBytes, totalBytes);
                return null;
            });
        }

        // Download asset index
        if (manifest.getAssetIndex() != null) {
            FileEntry assetIndexFe = new FileEntry();
            assetIndexFe.setPath("assets/indexes/" + manifest.id + ".json");
            assetIndexFe.setUrl(manifest.getAssetIndex().getUrl());
            assetIndexFe.setSha256(manifest.getAssetIndex().getSha1());
            assetIndexFe.setSize(manifest.getAssetIndex().getSize());
            tasks.add(() -> {
                downloadFile(assetIndexFe, LauncherPaths.getBaseDir(), tmp, listener, downloadedBytes, totalBytes);
                return null;
            });
        }

        // Download libraries
        if (manifest.getLibraries() != null) {
            for (BuildManifest.Library lib : manifest.getLibraries()) {
                if (!isApplicable(lib)) continue;
                BuildManifest.Downloads d = lib.getDownloads();
                if (d == null) continue;

                // Regular library artifact
                BuildManifest.Artifact artifact = d.getArtifact();
                if (artifact != null) {
                    FileEntry fe = new FileEntry();
                    fe.setPath("libraries/" + buildMavenPath(lib.getName()));
                    fe.setUrl(artifact.getUrl());
                    fe.setSha256(artifact.getSha1());
                    fe.setSize(artifact.getSize());
                    tasks.add(() -> {
                        downloadFile(fe, LauncherPaths.getBaseDir(), tmp, listener, downloadedBytes, totalBytes);
                        return null;
                    });
                }

                // Classifiers (natives etc.)
                java.util.Map<String, BuildManifest.Artifact> classifiers = d.getClassifiers();
                if (classifiers != null && !classifiers.isEmpty()) {
                    for (java.util.Map.Entry<String, BuildManifest.Artifact> ce : classifiers.entrySet()) {
                        String classifierKey = ce.getKey();
                        BuildManifest.Artifact a = ce.getValue();
                        if (a == null) continue;
                        // Only download native classifiers for current OS or useful ones
                        String lower = classifierKey.toLowerCase();
                        boolean want = lower.contains("natives") && (lower.contains("windows") || lower.contains("linux") || lower.contains("osx") || lower.contains("mac"));
                        if (!want) continue;

                        // Guess extension from URL
                        String url = a.getUrl();
                        String ext = "jar";
                        int idx = url.lastIndexOf('.');
                        if (idx > 0 && idx < url.length() - 1) ext = url.substring(idx + 1);

                        FileEntry fe = new FileEntry();
                        fe.setPath("libraries/" + buildMavenPathWithClassifier(lib.getName(), classifierKey, ext));
                        fe.setUrl(a.getUrl());
                        fe.setSha256(a.getSha1());
                        fe.setSize(a.getSize());
                        tasks.add(() -> {
                            downloadFile(fe, LauncherPaths.getBaseDir(), tmp, listener, downloadedBytes, totalBytes);
                            // After download, attempt to extract natives into baseDir/natives
                            try {
                                Path downloaded = LauncherPaths.getBaseDir().resolve(fe.getPath());
                                extractIfArchive(downloaded, LauncherPaths.getBaseDir().resolve("natives"));
                            } catch (Exception ex) {
                                // extraction errors should not fail whole install, just log
                                ex.printStackTrace();
                            }
                            return null;
                        });
                    }
                }
            }
        }

        List<Future<Void>> futures = executor.invokeAll(tasks);
        for (Future<Void> f : futures) {
            try {
                f.get();
            } catch (ExecutionException ex) {
                throw new RuntimeException("Download failed", ex.getCause());
            }
        }

        downloadAssets(manifest, listener, downloadedBytes, totalBytes);
    }

    private void downloadFile(FileEntry fe, Path baseDir, Path tmp, ProgressListener listener, AtomicLong downloadedBytes, AtomicLong totalBytes) throws Exception {
        Path dest = baseDir.resolve(fe.getPath());
        if (FileUtils.existsAndMatches(dest, fe.getSha256())) {
            if (listener != null) listener.onFileCompleted(fe);
            return;
        }

        Path tmpDest = tmp.resolve(java.util.UUID.randomUUID().toString() + ".part");
        final long[] lastRead = {0L};
        Exception lastException = null;

        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                // download with progress
                HttpClientHelper.downloadToFile(fe.getUrl(), tmpDest, (read, total) -> {
                    if (listener != null) listener.onFileProgress(fe, read, total);
                    long delta = read - lastRead[0];
                    if (delta > 0) {
                        downloadedBytes.addAndGet(delta);
                        lastRead[0] = read;
                        if (listener != null) listener.onOverallProgress(downloadedBytes.get(), totalBytes.get());
                    }
                });

                // try atomic move, fall back to copy/stream if necessary
                try {
                    FileUtils.moveAtomically(tmpDest, dest);
                } catch (Exception moveEx) {
                    boolean copied = false;
                    try { Files.createDirectories(dest.getParent()); } catch (Exception ignore) {}
                    for (int c = 0; c < 3 && !copied; c++) {
                        try {
                            Files.copy(tmpDest, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            Files.deleteIfExists(tmpDest);
                            copied = true;
                            break;
                        } catch (Exception e) {
                            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                        }
                    }
                    if (!copied) {
                        try (java.io.InputStream in = Files.newInputStream(tmpDest);
                             java.io.OutputStream out = Files.newOutputStream(dest, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                            byte[] buf = new byte[8192];
                            int r;
                            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
                            out.flush();
                            try { Files.deleteIfExists(tmpDest); } catch (Exception ignored) {}
                        }
                    }
                }

                // success
                lastException = null;
                break;
            } catch (Exception ex) {
                lastException = ex;
                try { Files.deleteIfExists(tmpDest); } catch (Exception ignored) {}
                if (attempt < 2) {
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    System.out.println("Retrying download for " + fe.getPath() + " attempt=" + (attempt+2));
                    continue;
                }
            }
        }

        if (lastException != null) {
            if (listener != null) listener.onFileFailed(fe, lastException);
            throw lastException;
        }

        // Account for any remaining bytes if manifest provided size but no progress callbacks occurred
        long reported = lastRead[0];
        long expected = fe.getSize();
        if (expected > 0 && expected > reported) {
            long delta = expected - reported;
            downloadedBytes.addAndGet(delta);
            if (listener != null) listener.onOverallProgress(downloadedBytes.get(), totalBytes.get());
        }

        if (listener != null) listener.onFileCompleted(fe);
    }

    private boolean isApplicable(BuildManifest.Library lib) {
        return true;
    }

    private String buildMavenPath(String name) {
        String[] parts = name.split(":");
        return parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2] + ".jar";
    }

    private String buildMavenPathWithClassifier(String name, String classifier, String ext) {
        String[] parts = name.split(":");
        return parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2] + "-" + classifier + "." + ext;
    }

    private void extractIfArchive(Path archive, Path destDir) throws Exception {
        if (archive == null || !Files.exists(archive)) return;
        String fileName = archive.getFileName().toString().toLowerCase();
        if (!(fileName.endsWith(".zip") || fileName.endsWith(".jar"))) return;
        Files.createDirectories(destDir);
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                Path out = destDir.resolve(entry.getName());
                Files.createDirectories(out.getParent());
                try (java.io.OutputStream os = Files.newOutputStream(out, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = zis.read(buf)) != -1) os.write(buf, 0, r);
                }
                zis.closeEntry();
            }
        }
    }

    private void downloadAssets(BuildManifest manifest, ProgressListener listener, AtomicLong downloadedBytes, AtomicLong totalBytes) throws Exception {
        Path assetIndexPath = LauncherPaths.getBaseDir().resolve("assets/indexes/" + manifest.id + ".json");
        if (!Files.exists(assetIndexPath)) return;
        String json = Files.readString(assetIndexPath);
        AssetIndexContent content = gson.fromJson(json, AssetIndexContent.class);
        List<Callable<Void>> assetTasks = new ArrayList<>();
        Path tmp = LauncherPaths.getTempDir();

        long assetsTotal = 0L;
        for (Map.Entry<String, AssetIndexContent.AssetObject> entry : content.getObjects().entrySet()) {
            assetsTotal += entry.getValue().getSize();
        }
        totalBytes.addAndGet(assetsTotal);
        if (listener != null) listener.onOverallProgress(downloadedBytes.get(), totalBytes.get());

        for (Map.Entry<String, AssetIndexContent.AssetObject> entry : content.getObjects().entrySet()) {
            String hash = entry.getValue().getHash();
            long size = entry.getValue().getSize();
            String url = "https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash;
            FileEntry fe = new FileEntry();
            fe.setPath("assets/objects/" + hash.substring(0, 2) + "/" + hash);
            fe.setUrl(url);
            fe.setSha256(hash);
            fe.setSize(size);
            assetTasks.add(() -> {
                downloadFile(fe, LauncherPaths.getBaseDir(), tmp, listener, downloadedBytes, totalBytes);
                return null;
            });
        }
        List<Future<Void>> assetFutures = executor.invokeAll(assetTasks);
        for (Future<Void> f : assetFutures) {
            f.get();
        }
    }

    public static class AssetIndexContent {
        private Map<String, AssetObject> objects;

        public static class AssetObject {
            private String hash;
            private long size;
            public String getHash() { return hash; }
            public long getSize() { return size; }
        }

        public Map<String, AssetObject> getObjects() { return objects; }
    }
}
