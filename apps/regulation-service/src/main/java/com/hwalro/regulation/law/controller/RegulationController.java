package com.hwalro.regulation.law.controller;

import com.hwalro.regulation.law.dto.RegulationDetail;
import com.hwalro.regulation.law.dto.RegulationSearchResponse;
import com.hwalro.regulation.law.dto.RelatedRegulation;
import com.hwalro.regulation.law.service.RegulationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/regulations")
/** 법령 목록과 상세 조회를 프론트엔드에 제공하는 HTTP 진입점이다. */
public class RegulationController {
    private final RegulationService regulationService;

    public RegulationController(RegulationService regulationService) {
        this.regulationService = regulationService;
    }

    @GetMapping
    /** 검색어가 없으면 안전 관련 기본 목록을, 있으면 해당 검색 결과를 반환한다. */
    public RegulationSearchResponse search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return regulationService.search(query, page, size);
    }

    @GetMapping("/by-law-id/{lawId}")
    /** 관련 법령 카드의 법령 ID로 상세 정보를 반환한다. */
    public RegulationDetail detailByLawId(@PathVariable String lawId) {
        return regulationService.getDetailByLawId(lawId);
    }

    @GetMapping("/{lawId}/related-laws")
    /** 선택 법령의 공식 관련 법령 목록을 반환한다. */
    public List<RelatedRegulation> relatedLaws(@PathVariable String lawId) {
        return regulationService.getRelatedLaws(lawId);
    }

    @GetMapping("/{serialNumber}")
    /** 목록 응답의 법령일련번호(MST)로 선택 법령의 조문을 반환한다. */
    public RegulationDetail detail(@PathVariable String serialNumber) {
        return regulationService.getDetail(serialNumber);
    }
}
