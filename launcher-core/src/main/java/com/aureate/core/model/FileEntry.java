package com.aureate.core.model;

public class FileEntry {
    private String path;
    private String sha256;
    private long size;
    private String url;

    public FileEntry() {}

    public FileEntry(String path, String sha256, long size, String url) {
        this.path = path;
        this.sha256 = sha256;
        this.size = size;
        this.url = url;
    }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    @Override
    public String toString() {
        return "FileEntry{" +
                "path='" + path + '\'' +
                ", sha256='" + sha256 + '\'' +
                ", size=" + size +
                ", url='" + url + '\'' +
                '}';
    }
}
