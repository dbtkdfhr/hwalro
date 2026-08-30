package com.hwalro.regulation.report.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hwalro.regulation.report.client.SimulationReportContextClient.Context;
import java.util.List;

public record ReportDraftInput(Context source, List<Context> comparisons, List<Risk> risks) {
    public record Risk(
            Long layoutId, Long layoutVersionId, String title, String description, String severity, List<Law> laws) {
        public Risk(Long layoutId, String title, String description, String severity) {
            this(layoutId, null, title, description, severity, List.of());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Law(String serialNumber, String name, String articleNumber, String articleTitle, String content) {}
}
