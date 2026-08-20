package com.hwalro.simulation.search.diagnosis;

import com.hwalro.simulation.search.domain.Diagnosis;
import com.hwalro.simulation.search.domain.Finding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DiagnosisAssembler {
    public Diagnosis assemble(List<List<Finding>> extractorResults, int maxFindings) {
        List<Finding> findings = new ArrayList<>();
        for (List<Finding> extractorResult : extractorResults) {
            for (Finding finding : extractorResult) {
                double severity = clamp01(finding.severity());
                if (severity <= 0) {
                    continue;
                }
                findings.add(new Finding(
                        finding.type(), severity, finding.region(), finding.evidence(), finding.description()));
            }
        }
        findings.sort(Comparator.comparingDouble(Finding::severity).reversed());
        if (findings.size() > maxFindings) {
            findings = new ArrayList<>(findings.subList(0, maxFindings));
        }
        return new Diagnosis(findings);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
