package com.aureate.core.model;

public class LoaderInfo {
    private String type;
    private String version;

    public LoaderInfo() {}

    public LoaderInfo(String type, String version) {
        this.type = type;
        this.version = version;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    @Override
    public String toString() {
        return "LoaderInfo{" +
                "type='" + type + '\'' +
                ", version='" + version + '\'' +
                '}';
    }
}
