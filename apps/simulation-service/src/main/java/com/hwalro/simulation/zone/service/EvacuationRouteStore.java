package com.hwalro.simulation.zone.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.zone.dto.EvacuationRouteResponse;
import com.hwalro.simulation.zone.mapper.EvacuationRouteStoreMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 대피 경로 계산 결과를 MySQL에 영속 보관한다.
 */
@Component
public class EvacuationRouteStore {
    private static final Logger log = LoggerFactory.getLogger(EvacuationRouteStore.class);

    private static final int MAX_ROWS = 32;
    private static final TypeReference<List<EvacuationRouteResponse>> PAYLOAD_TYPE = new TypeReference<>() {};

    private final EvacuationRouteStoreMapper mapper;
    private final ObjectMapper objectMapper;

    public EvacuationRouteStore(EvacuationRouteStoreMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /** 저장된 전체 구역 결과. 없거나 읽지 못하면 null. */
    public List<EvacuationRouteResponse> find(Long layoutVersionId, String key) {
        try {
            String payload = mapper.findPayloadByKey(hash(key));
            if (payload == null) {
                return null;
            }
            return objectMapper.readValue(payload, PAYLOAD_TYPE);
        } catch (Exception exception) {
            log.warn("대피 경로 저장 결과를 읽지 못해 다시 계산합니다. layoutVersionId={}", layoutVersionId, exception);
            return null;
        }
    }

    public List<EvacuationRouteResponse> findLatest(Long layoutVersionId) {
        try {
            String payload = mapper.findLatestPayloadByVersionId(layoutVersionId);
            if (payload == null) {
                return null;
            }
            return objectMapper.readValue(payload, PAYLOAD_TYPE);
        } catch (Exception exception) {
            log.warn("대피 경로 이전 결과를 읽지 못했습니다. layoutVersionId={}", layoutVersionId, exception);
            return null;
        }
    }

    public void save(Long layoutId, Long layoutVersionId, String key, List<EvacuationRouteResponse> routes) {
        if (routes.isEmpty()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(routes);
            String hashedKey = hash(key);
            mapper.insert(hashedKey, layoutId, layoutVersionId, payload);
            mapper.deleteByVersionId(layoutVersionId, hashedKey);
            mapper.trimToLimit(MAX_ROWS);
        } catch (Exception exception) {
            log.warn("대피 경로 계산 결과를 저장하지 못했습니다. layoutVersionId={}", layoutVersionId, exception);
        }
    }

    static String hash(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 MessageDigest를 사용할 수 없습니다.", exception);
        }
    }
}
