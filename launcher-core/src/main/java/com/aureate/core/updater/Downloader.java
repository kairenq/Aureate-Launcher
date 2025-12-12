package com.aureate.core.updater;

import com.aureate.core.LauncherPaths;
import com.aureate.core.http.HttpClientHelper;
import com.aureate.core.model.BuildManifest;
import com.aureate.core.model.FileEntry;
import com.aureate.core.util.FileUtils;
import com.aureate.core.util.SHA256Util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class Downloader {
    public interface ProgressListener {
        void onFileProgress(FileEntry entry, long downloaded, long total);
        void onFileCompleted(FileEntry entry);
        void onFileFailed(FileEntry entry, Exception ex);
    }

    private final ExecutorService executor;
    private final int parallel;

    public Downloader(int parallel) {
        this.parallel = Math.max(1, parallel);
        this.executor = Executors.newFixedThreadPool(this.parallel);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public void installManifest(BuildManifest manifest, ProgressListener listener) throws InterruptedException {
        List<Callable<Void>> tasks = new ArrayList<>();
        Path instanceBase = LauncherPaths.getInstanceDir(manifest.getBuildId());
        Path tmp = LauncherPaths.getTempDir();
        try {
            Files.createDirectories(instanceBase);
            Files.createDirectories(tmp);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create dirs", e);
        }

        for (FileEntry fe : manifest.getFiles()) {
            tasks.add(() -> {
                Path dest = instanceBase.resolve(fe.getPath());
                // if exists and matches, skip
                if (FileUtils.existsAndMatches(dest, fe.getSha256())) {
                    if (listener != null) listener.onFileCompleted(fe);
                    return null;
                }
                // download to temp
                Path tmpDest = tmp.resolve(manifest.getBuildId() + "-" + fe.getPath().replace("/", "_") + ".part");
                try {
                    HttpClientHelper.downloadToFile(fe.getUrl(), tmpDest, (read, total) -> {
                        if (listener != null) listener.onFileProgress(fe, read, total);
                    });

                    // verify sha
                    String sha = SHA256Util.sha256(tmpDest);
                    if (!sha.equalsIgnoreCase(fe.getSha256())) {
                        throw new RuntimeException("SHA mismatch for " + fe.getPath() + " expected=" + fe.getSha256() + " got=" + sha);
                    }

                    // move atomically to dest
                    FileUtils.moveAtomically(tmpDest, dest);
                    if (listener != null) listener.onFileCompleted(fe);
                } catch (Exception ex) {
                    // cleanup tmp file
                    try { Files.deleteIfExists(tmpDest); } catch (Exception ignored) {}
                    if (listener != null) listener.onFileFailed(fe, ex);
                    throw ex;
                }
                return null;
            });
        }

        List<Future<Void>> futures = executor.invokeAll(tasks);
        // check for exceptions
        for (Future<Void> f : futures) {
            try {
                f.get();
            } catch (ExecutionException ex) {
                throw new RuntimeException("Download failed", ex.getCause());
            }
        }
    }
}
