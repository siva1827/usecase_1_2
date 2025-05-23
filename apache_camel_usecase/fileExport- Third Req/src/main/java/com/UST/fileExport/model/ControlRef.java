package com.UST.fileExport.model;

public class ControlRef {
    private String id;
    private String lastProcessTs;

    public String get_id() {
        return id;
    }

    public void set_id(String id) {
        this.id = id;
    }

    public String getLastProcessTs() {
        return lastProcessTs;
    }

    public void setLastProcessTs(String lastProcessTs) {
        this.lastProcessTs = lastProcessTs;
    }
}