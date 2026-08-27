package com.hwalro.simulation.zone.mapper;

import com.hwalro.simulation.zone.domain.LayoutZone;
import com.hwalro.simulation.zone.domain.LayoutZoneMember;
import com.hwalro.simulation.zone.dto.AssignedZoneRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LayoutZoneMapper {

    List<LayoutZone> findZonesByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    LayoutZone findZoneById(@Param("id") Long id);

    List<AssignedZoneRow> findAssignedZonesByUserId(@Param("userId") Long userId);

    int countZonesAssignedToUserInVersion(@Param("layoutVersionId") Long layoutVersionId, @Param("userId") Long userId);

    int insertZone(LayoutZone zone);

    int updateZone(LayoutZone zone);

    int deleteZoneById(@Param("id") Long id);

    List<LayoutZoneMember> findZoneMembersByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    Long findZoneIdByFabricId(@Param("layoutVersionId") Long layoutVersionId, @Param("fabricId") Long fabricId);

    int deleteZoneMembersByZoneId(@Param("zoneId") Long zoneId);

    int insertZoneMembers(@Param("members") List<LayoutZoneMember> members);

    /** 도면 복제·개선안 채택에서 배치 제외 사각형을 그대로 옮긴다. 참조가 없어 ID 매핑이 필요 없다. */
}
