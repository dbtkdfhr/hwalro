package com.hwalro.regulation.report.ai;

import com.hwalro.regulation.report.dto.ReportContent;

public interface ReportDraftGenerator {
    ReportContent generate(ReportDraftInput input);
}
