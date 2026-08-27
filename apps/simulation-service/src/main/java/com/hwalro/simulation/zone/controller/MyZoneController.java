package com.hwalro.simulation.zone.controller;

import com.hwalro.simulation.common.jwt.JwtAuthInterceptor;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.common.jwt.RequireRole;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.MyZoneResponse;
import com.hwalro.simulation.zone.service.LayoutMetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인한 사용자에게 배정된 구역만 반환한다. 사용자 식별은 액세스 토큰만 사용한다. */
@RestController
@RequestMapping("/api/my-zones")
@Tag(name = "My Zones", description = "내 담당 구역 API")
@RequireRole({"GENERAL_EMPLOYEE", "OPERATOR", "SAFETY_REVIEWER", "ADMIN"})
public class MyZoneController {
    private final LayoutMetadataService layoutMetadataService;

    public MyZoneController(LayoutMetadataService layoutMetadataService) {
        this.layoutMetadataService = layoutMetadataService;
    }

    @GetMapping
    @Operation(summary = "내 담당 구역 목록", description = "현재 로그인한 사용자에게 배정된 구역과 그 구역이 속한 도면을 반환합니다.")
    public List<MyZoneResponse> myZones(
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return layoutMetadataService.myZones(user);
    }
}
