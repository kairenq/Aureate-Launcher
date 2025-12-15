package com.aureate.core.model;

import java.util.List;

public class BuildManifest {
    private String buildId;
    private String name;
    private String description;
    private String mcVersion;
    private LoaderInfo loader;
    private JavaInfo java;
    private List<FileEntry> files;
    private Meta meta;
    private String mainClass;
    private List<Library> libraries;
    private AssetIndex assetIndex;
    private VersionDownloads downloads;

    // For version list
    public String id;
    public String url;

    // Existing classes...

    public static class Library {
        private String name;
        private Downloads downloads;
        // Add rules if needed
        public String getName() { return name; }
        public Downloads getDownloads() { return downloads; }
    }

    public static class Downloads {
        private Artifact artifact;
        private java.util.Map<String, Artifact> classifiers;
        public Artifact getArtifact() { return artifact; }
        public java.util.Map<String, Artifact> getClassifiers() { return classifiers; }
    }

    public static class Artifact {
        private String url;
        private String sha1;
        private long size;
        public String getUrl() { return url; }
        public String getSha1() { return sha1; }
        public long getSize() { return size; }
    }

    public static class VersionDownloads {
        private Artifact client;
        private Artifact server;
        public Artifact getClient() { return client; }
        public Artifact getServer() { return server; }
    }

    public static class AssetIndex {
        private String id;
        private String url;
        private String sha1;
        private long size;
        public String getId() { return id; }
        public String getUrl() { return url; }
        public String getSha1() { return sha1; }
        public long getSize() { return size; }
    }

    // Getters
    public String getMainClass() { return mainClass; }
    public List<Library> getLibraries() { return libraries; }
    public AssetIndex getAssetIndex() { return assetIndex; }
    public VersionDownloads getDownloads() { return downloads; }

    public static class JavaInfo {
        private int major;
        private int minRamMb;
        private int maxRamMb;
        public JavaInfo() {}
        public int getMajor() { return major; }
        public void setMajor(int major) { this.major = major; }
        public int getMinRamMb() { return minRamMb; }
        public void setMinRamMb(int minRamMb) { this.minRamMb = minRamMb; }
        public int getMaxRamMb() { return maxRamMb; }
        public void setMaxRamMb(int maxRamMb) { this.maxRamMb = maxRamMb; }
        @Override public String toString(){ return "JavaInfo{major="+major+"}"; }
    }

    public static class Meta {
        private String icon;
        private List<String> screenshots;
        private String changelog;
        public Meta() {}
        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
        public List<String> getScreenshots() { return screenshots; }
        public void setScreenshots(List<String> screenshots) { this.screenshots = screenshots; }
        public String getChangelog() { return changelog; }
        public void setChangelog(String changelog) { this.changelog = changelog; }
    }

    public BuildManifest() {}

    public String getBuildId() { return buildId; }
    public void setBuildId(String buildId) { this.buildId = buildId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMcVersion() { return mcVersion; }
    public void setMcVersion(String mcVersion) { this.mcVersion = mcVersion; }

    public LoaderInfo getLoader() { return loader; }
    public void setLoader(LoaderInfo loader) { this.loader = loader; }

    public JavaInfo getJava() { return java; }
    public void setJava(JavaInfo java) { this.java = java; }

    public List<FileEntry> getFiles() { return files; }
    public void setFiles(List<FileEntry> files) { this.files = files; }

    public Meta getMeta() { return meta; }
    public void setMeta(Meta meta) { this.meta = meta; }

    @Override
    public String toString() {
        return "Vanilla " + id;
    }
}
