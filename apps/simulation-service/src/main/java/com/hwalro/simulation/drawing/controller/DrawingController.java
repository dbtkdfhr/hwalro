package com.hwalro.simulation.drawing.controller;

import com.hwalro.simulation.common.jwt.JwtAuthInterceptor;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.common.jwt.RequireRole;
import com.hwalro.simulation.drawing.dto.DrawingCreateRequest;
import com.hwalro.simulation.drawing.dto.DrawingListResponse;
import com.hwalro.simulation.drawing.dto.DrawingResponse;
import com.hwalro.simulation.drawing.dto.DrawingUpdateRequest;
import com.hwalro.simulation.drawing.service.DrawingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drawings")
@Tag(name = "Drawings", description = "도면 관리 API")
@RequireRole({"OPERATOR", "SAFETY_REVIEWER", "ADMIN"})
public class DrawingController {
    private final DrawingService drawingService;

    public DrawingController(DrawingService drawingService) {
        this.drawingService = drawingService;
    }

    @GetMapping
    @Operation(
            summary = "도면 목록 조회",
            description = "페이지네이션을 지원합니다. 운영 담당자는 본인이 생성한 도면만, 안전 검토자와 관리자는 전체 도면을 조회할 수 있습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "도면 목록 조회 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 페이지 요청")
    })
    public DrawingListResponse list(
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @Parameter(description = "1부터 시작하는 페이지 번호") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지당 조회 건수") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "도면명 검색어") @RequestParam(required = false) String query) {
        return drawingService.list(page, size, query, user);
    }

    @GetMapping("/{id}")
    @Operation(summary = "도면 상세 조회", description = "도면 정보와 벽·텍스트 배치 데이터를 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "도면 상세 조회 성공"),
        @ApiResponse(responseCode = "403", description = "접근 권한 없음"),
        @ApiResponse(responseCode = "404", description = "도면을 찾을 수 없음")
    })
    public DrawingResponse get(
            @Parameter(description = "도면 ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return drawingService.get(id, user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "도면 생성",
            description =
                    "새 도면을 생성합니다. withDefaultData가 true이면 기본 도면 데이터를 포함하고, false이면 빈 도면으로 생성합니다. 제목을 생략하면 기본 도면 이름이 사용됩니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "도면 생성 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    public DrawingResponse create(
            @Parameter(description = "도면 생성 요청") @RequestBody DrawingCreateRequest request,
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return drawingService.create(request, user.userId());
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "도면 복제", description = "도면과 현재 버전의 배치 데이터를 복사해 새 도면을 만듭니다. 복제본 제목은 '원본 제목 복사본'으로 지정됩니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "도면 복제 성공"),
        @ApiResponse(responseCode = "403", description = "접근 권한 없음"),
        @ApiResponse(responseCode = "404", description = "도면을 찾을 수 없음")
    })
    public DrawingResponse duplicate(
            @Parameter(description = "도면 ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return drawingService.duplicate(id, user);
    }

    @PutMapping("/{id}")
    @Operation(summary = "도면 저장", description = "도면 제목·설명과 벽·텍스트 배치 데이터를 저장합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "도면 저장 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "403", description = "접근 권한 없음"),
        @ApiResponse(responseCode = "404", description = "도면을 찾을 수 없음")
    })
    public DrawingResponse update(
            @Parameter(description = "도면 ID") @PathVariable Long id,
            @Parameter(description = "도면 저장 요청") @RequestBody DrawingUpdateRequest request,
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return drawingService.update(id, request, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "도면 삭제", description = "도면과 연결된 배치 데이터를 함께 삭제합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "도면 삭제 성공"),
        @ApiResponse(responseCode = "403", description = "접근 권한 없음"),
        @ApiResponse(responseCode = "404", description = "도면을 찾을 수 없음")
    })
    public void delete(
            @Parameter(description = "도면 ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        drawingService.delete(id, user);
    }
}
