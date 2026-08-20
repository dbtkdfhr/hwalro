package com.hwalro.regulation.safetycheck.domain;

public class ChecklistTemplate {
    private Long id;
    private Long inspectionAreaId;
    private int version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getInspectionAreaId() {
        return inspectionAreaId;
    }

    public void setInspectionAreaId(Long inspectionAreaId) {
        this.inspectionAreaId = inspectionAreaId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
