package com.aureate.core.http;

import okhttp3.*;
import okio.BufferedSink;
import okio.Okio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class HttpClientHelper {
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .callTimeout(10, TimeUnit.MINUTES)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .build();

    public interface ProgressCallback {
        void onProgress(long bytesRead, long contentLength);
    }

    public static void downloadToFile(String url, Path dest, ProgressCallback progressCallback) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        try (Response resp = CLIENT.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Failed download: " + resp.code() + " " + resp.message());
            }
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("Empty body for " + url);

            long contentLength = body.contentLength();
            try (InputStream in = body.byteStream()) {
                Files.createDirectories(dest.getParent());
                // write to temp file then move
                Path tmp = dest.resolveSibling(dest.getFileName().toString() + ".part");
                try (BufferedSink sink = Okio.buffer(Okio.sink(tmp))) {
                    byte[] buf = new byte[8192];
                    int read;
                    long totalRead = 0;
                    while ((read = in.read(buf)) != -1) {
                        sink.write(buf, 0, read);
                        totalRead += read;
                        if (progressCallback != null) progressCallback.onProgress(totalRead, contentLength);
                    }
                    sink.flush();
                }
                Files.move(tmp, dest);
            }
        }
    }

    public static String getString(String url) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        try (Response resp = CLIENT.newCall(request).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
            ResponseBody body = resp.body();
            if (body == null) return null;
            return body.string();
        }
    }
}
