package com.hwalro.regulation.report.ai;

import com.hwalro.regulation.report.client.SimulationReportContextClient.Context;
import java.util.List;

public record ReportDraftInput(Context source, List<Context> comparisons, List<Risk> risks) {
    public record Risk(Long simulationResultId, String title, String description, String severity) {}
}
