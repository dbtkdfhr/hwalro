package com.hwalro.simulation.zone.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EvacuationRouteStoreMapper {

    String findPayloadByKey(@Param("cacheKey") String cacheKey);

    String findLatestPayloadByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    int insert(
            @Param("cacheKey") String cacheKey,
            @Param("layoutId") Long layoutId,
            @Param("layoutVersionId") Long layoutVersionId,
            @Param("resultPayload") String resultPayload);

    int deleteByVersionId(@Param("layoutVersionId") Long layoutVersionId, @Param("cacheKey") String cacheKey);

    void trimToLimit(@Param("limit") int limit);
}
