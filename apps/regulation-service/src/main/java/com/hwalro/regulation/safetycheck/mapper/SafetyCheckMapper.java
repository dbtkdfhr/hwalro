package com.hwalro.regulation.safetycheck.mapper;

import com.hwalro.regulation.safetycheck.domain.ChecklistTemplate;
import com.hwalro.regulation.safetycheck.domain.InspectionArea;
import com.hwalro.regulation.safetycheck.domain.SafetyInspection;
import com.hwalro.regulation.safetycheck.dto.ChecklistTemplateItemResponse;
import com.hwalro.regulation.safetycheck.dto.InspectionAreaResponse;
import com.hwalro.regulation.safetycheck.dto.InspectionDetailHeader;
import com.hwalro.regulation.safetycheck.dto.InspectionHistoryResponse;
import com.hwalro.regulation.safetycheck.dto.InspectionItemResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SafetyCheckMapper {
    List<InspectionAreaResponse> findAreas(@Param("inspectorId") Long inspectorId);

    InspectionAreaResponse findArea(@Param("areaId") Long areaId, @Param("inspectorId") Long inspectorId);

    int insertArea(InspectionArea area);

    int updateArea(InspectionArea area);

    int deactivateArea(@Param("areaId") Long areaId);

    boolean areaExists(@Param("areaId") Long areaId);

    Long findAreaLayoutId(@Param("areaId") Long areaId);

    Long lockInspectionArea(@Param("areaId") Long areaId);

    List<InspectionHistoryResponse> findInspectionHistory(
            @Param("areaId") Long areaId, @Param("inspectorId") Long inspectorId);

    InspectionDetailHeader findInspectionHeader(@Param("inspectionId") Long inspectionId);

    List<InspectionItemResponse> findInspectionItems(@Param("inspectionId") Long inspectionId);

    Long findActiveTemplateId(@Param("areaId") Long areaId);

    Long findOpenDraftId(@Param("areaId") Long areaId, @Param("inspectorId") Long inspectorId);

    Integer findTemplateVersion(@Param("templateId") Long templateId);

    List<ChecklistTemplateItemResponse> findTemplateItems(@Param("templateId") Long templateId);

    int findNextTemplateVersion(@Param("areaId") Long areaId);

    int retireActiveTemplates(@Param("areaId") Long areaId);

    int insertChecklistTemplate(ChecklistTemplate template);

    int insertChecklistTemplateItem(
            @Param("templateId") Long templateId,
            @Param("title") String title,
            @Param("criterion") String criterion,
            @Param("category") String category,
            @Param("displayOrder") int displayOrder);

    int insertInspection(SafetyInspection inspection);

    int insertInspectionItems(@Param("inspectionId") Long inspectionId, @Param("templateId") Long templateId);

    int updateInspectionItem(
            @Param("inspectionId") Long inspectionId,
            @Param("itemId") Long itemId,
            @Param("result") String result,
            @Param("comment") String comment,
            @Param("markerX") Double markerX,
            @Param("markerY") Double markerY,
            @Param("checkedAt") LocalDateTime checkedAt);

    int updateSnapshotImage(
            @Param("inspectionId") Long inspectionId,
            @Param("image") byte[] image,
            @Param("layoutId") Long layoutId,
            @Param("layoutVersionId") Long layoutVersionId);

    byte[] findSnapshotImage(@Param("inspectionId") Long inspectionId);

    int countInspectionItems(@Param("inspectionId") Long inspectionId);

    int countPendingItems(@Param("inspectionId") Long inspectionId);

    int updateInspection(
            @Param("inspectionId") Long inspectionId,
            @Param("status") String status,
            @Param("comment") String comment,
            @Param("completedAt") LocalDateTime completedAt);

    int deleteInspection(@Param("inspectionId") Long inspectionId);
}
