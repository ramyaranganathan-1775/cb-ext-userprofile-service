package com.igot.cb.health.service;

import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.elasticsearch.service.EsClientService;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthServiceImplTest {

    @Mock CassandraOperation cassandraOperation;
    @Mock CacheService redisCacheService;
    @Mock EntityManager entityManager;
    @Mock Query query;
    @Mock
    EsClientService esClientService;
    private final String REQUEST_ID = "test-request--123";
    @InjectMocks HealthServiceImpl service;
    @Mock
    ApiResponse response;

    // 🔹 Common mocks
    void mockAllHealthy() throws Exception {

        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(List.of(Map.of("k", "v")));

        when(redisCacheService.isRedisHealthy()).thenReturn(true);

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1);

        when(esClientService.isElasticsearchHealthy()).thenReturn(true);
    }

    // ✅ SUCCESS CASE
    @Test
    void testHealthCheckSuccess() throws Exception {

        mockAllHealthy();

        response = service.checkHealthStatus(REQUEST_ID);

        assertNotNull(response);


        Map<String, Object> result =
                (Map<String, Object>) response.get(Constants.RESPONSE);

        assertNotNull(response);
        assertEquals(Constants.ALL_HEALTH_CHECK, result.get(Constants.NAME));

        List<Map<String, Object>> checks =
                (List<Map<String, Object>>) result.get(Constants.CHECKS);

        assertEquals(4, checks.size());
    }

    // ❌ FAILURE CASE (Redis down)
    @Test
    void testRedisFailure() throws Exception {

        mockAllHealthy();
        when(redisCacheService.isRedisHealthy()).thenReturn(false);

        response = service.checkHealthStatus(REQUEST_ID);

        assertFalse(Boolean.TRUE.equals(response.get(Constants.HEALTHY)));
    }

    // ❌ FAILURE CASE (Postgres down)
    @Test
    void testPostgresFailure() throws Exception {

        mockAllHealthy();

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenThrow(new RuntimeException("DB error"));

        ApiResponse response = service.checkHealthStatus(REQUEST_ID);

        assertFalse(Boolean.TRUE.equals(response.get(Constants.HEALTHY)));
    }

    // 💥 EXCEPTION CASE
    @Test
    void testExceptionHandling() throws Exception {

        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB failure"));

        ApiResponse response = service.checkHealthStatus("req-ex");

        assertNotNull(response);

        // ✅ overall unhealthy
        assertFalse(Boolean.TRUE.equals(response.get(Constants.HEALTHY)));

        // ✅ exception handled
        assertNotEquals(Constants.FAILED, response.getParams().getStatus());

    }

}



