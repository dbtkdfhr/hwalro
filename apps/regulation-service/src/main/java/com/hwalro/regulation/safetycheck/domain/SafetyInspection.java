package com.hwalro.regulation.safetycheck.domain;

public class SafetyInspection {
    private Long id;
    private Long inspectionAreaId;
    private Long checklistTemplateId;
    private Long simulationResultId;
    private Long inspectorId;

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

    public Long getChecklistTemplateId() {
        return checklistTemplateId;
    }

    public void setChecklistTemplateId(Long checklistTemplateId) {
        this.checklistTemplateId = checklistTemplateId;
    }

    public Long getSimulationResultId() {
        return simulationResultId;
    }

    public void setSimulationResultId(Long simulationResultId) {
        this.simulationResultId = simulationResultId;
    }

    public Long getInspectorId() {
        return inspectorId;
    }

    public void setInspectorId(Long inspectorId) {
        this.inspectorId = inspectorId;
    }
}
