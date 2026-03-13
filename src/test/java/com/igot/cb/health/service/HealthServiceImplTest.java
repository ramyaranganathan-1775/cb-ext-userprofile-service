package com.igot.cb.health.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.ClusterClient;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthServiceImplTest {

    @InjectMocks
    private HealthServiceImpl healthService;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private CacheService redisCacheService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query nativeQuery;

    @Mock
    private RestHighLevelClient elasticsearchClient;

    @Mock
    private ClusterClient clusterClient;

    @BeforeEach
    void setUp() {
        // Setup is handled by MockitoExtension
    }

    // ==================== Test: All Services Healthy ====================



    // ==================== Test: Redis Unhealthy ====================

    @Test
    void testCheckHealthStatus_RedisUnhealthy() throws Exception {
        // Arrange
        List<Map<String, Object>> cassandraResponse = new ArrayList<>();
        cassandraResponse.add(new HashMap<>());

        when(cassandraOperation.getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_SYSTEM_SETTINGS,
                null,
                null,
                null))
                .thenReturn(cassandraResponse);

        when(redisCacheService.isRedisHealthy()).thenReturn(false);

        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(1);

        // Act
        ApiResponse response = healthService.checkHealthStatus();

        // Assert
        assertNotNull(response);
        assertFalse((Boolean) response.get(Constants.HEALTHY));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) response.get(Constants.CHECKS);

        Map<String, Object> redisCheck = checks.stream()
                .filter(check -> Constants.REDIS_CACHE.equals(check.get(Constants.NAME)))
                .findFirst()
                .orElse(null);

        assertNotNull(redisCheck);
        assertFalse((Boolean) redisCheck.get(Constants.HEALTHY));
    }

    // ==================== Test: PostgreSQL Unhealthy ====================



    // ==================== Test: Response Structure ====================

    @Test
    void testCheckHealthStatus_ResponseStructureIsValid() throws Exception {
        // Arrange
        List<Map<String, Object>> cassandraResponse = new ArrayList<>();
        cassandraResponse.add(new HashMap<>());

        when(cassandraOperation.getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_SYSTEM_SETTINGS,
                null,
                null,
                null))
                .thenReturn(cassandraResponse);

        when(redisCacheService.isRedisHealthy()).thenReturn(true);

        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(1);

        // Act
        ApiResponse response = healthService.checkHealthStatus();

        // Assert
        assertNotNull(response);
        assertNotNull(response.getId());
        assertNotNull(response.getParams());
        assertNotNull(response.get(Constants.CHECKS));
        assertEquals(Constants.API_HEALTH_CHECK, response.getId());
        assertNotNull(response.getParams().getStatus());
    }

    // ==================== Test: Cassandra Health Status ====================

    @Test
    void testCassandraHealthStatus_WithHealthyResponse() throws Exception {
        // Arrange
        ApiResponse response = new ApiResponse(Constants.API_HEALTH_CHECK);
        response.put(Constants.HEALTHY, true);
        response.put(Constants.CHECKS, new ArrayList<>());

        List<Map<String, Object>> cassandraResponse = new ArrayList<>();
        cassandraResponse.add(new HashMap<>());

        when(cassandraOperation.getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_SYSTEM_SETTINGS,
                null,
                null,
                null))
                .thenReturn(cassandraResponse);

        // Act
        healthService.cassandraHealthStatus(response);

        // Assert
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) response.get(Constants.CHECKS);
        assertEquals(1, checks.size());

        Map<String, Object> check = checks.get(0);
        assertEquals(Constants.CASSANDRA_DB, check.get(Constants.NAME));
        assertTrue((Boolean) check.get(Constants.HEALTHY));
    }

    @Test
    void testCassandraHealthStatus_WithEmptyResponse() throws Exception {
        // Arrange
        ApiResponse response = new ApiResponse(Constants.API_HEALTH_CHECK);
        response.put(Constants.HEALTHY, true);
        response.put(Constants.CHECKS, new ArrayList<>());

        when(cassandraOperation.getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_SYSTEM_SETTINGS,
                null,
                null,
                null))
                .thenReturn(new ArrayList<>());

        // Act
        healthService.cassandraHealthStatus(response);

        // Assert
        assertFalse((Boolean) response.get(Constants.HEALTHY));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) response.get(Constants.CHECKS);
        assertEquals(1, checks.size());

        Map<String, Object> check = checks.get(0);
        assertFalse((Boolean) check.get(Constants.HEALTHY));
    }

    // ==================== Test: PostgreSQL Health Status ====================

    @Test
    void testPostgresHealthStatus_WithSuccessfulConnection() throws Exception {
        // Arrange
        ApiResponse response = new ApiResponse(Constants.API_HEALTH_CHECK);
        response.put(Constants.HEALTHY, true);
        response.put(Constants.CHECKS, new ArrayList<>());

        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(1);

        // Act
        healthService.postgresHealthStatus(response);

        // Assert
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) response.get(Constants.CHECKS);
        assertEquals(1, checks.size());

        Map<String, Object> check = checks.get(0);
        assertTrue((Boolean) check.get(Constants.HEALTHY));
        assertTrue((Boolean) response.get(Constants.HEALTHY));

        verify(entityManager, times(1)).createNativeQuery("SELECT 1");
    }

    @Test
    void testPostgresHealthStatus_WithFailedConnection() throws Exception {
        // Arrange
        ApiResponse response = new ApiResponse(Constants.API_HEALTH_CHECK);
        response.put(Constants.HEALTHY, true);
        response.put(Constants.CHECKS, new ArrayList<>());

        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenThrow(new RuntimeException("Connection timeout"));

        // Act
        healthService.postgresHealthStatus(response);

        // Assert
        assertFalse((Boolean) response.get(Constants.HEALTHY));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) response.get(Constants.CHECKS);
        assertEquals(1, checks.size());

        Map<String, Object> check = checks.get(0);
        assertFalse((Boolean) check.get(Constants.HEALTHY));
    }

    // ==================== Test: Mixed Scenarios ====================

    

    // ==================== Test: Verify Method Invocations ====================

    @Test
    void testCheckHealthStatus_VerifiesDependenciesAreCalled() throws Exception {
        // Arrange
        List<Map<String, Object>> cassandraResponse = new ArrayList<>();
        cassandraResponse.add(new HashMap<>());

        when(cassandraOperation.getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_SYSTEM_SETTINGS,
                null,
                null,
                null))
                .thenReturn(cassandraResponse);

        when(redisCacheService.isRedisHealthy()).thenReturn(true);

        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(1);

        // Act
        healthService.checkHealthStatus();

        // Assert - Verify all dependencies were called
        verify(cassandraOperation, times(1)).getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_SYSTEM_SETTINGS,
                null,
                null,
                null);

        verify(redisCacheService, times(1)).isRedisHealthy();

        verify(entityManager, times(1)).createNativeQuery("SELECT 1");
        verify(nativeQuery, times(1)).getSingleResult();
    }


    @Test
    void checkHealthStatus_AllServicesHealthy() throws Exception {
        List<Map<String, Object>> cassandraResponse = new ArrayList<>();
        cassandraResponse.add(new HashMap<>());

        when(cassandraOperation.getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_SYSTEM_SETTINGS,
                null,
                null,
                null))
                .thenReturn(cassandraResponse);

        when(redisCacheService.isRedisHealthy()).thenReturn(true);

        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(1);

        ClusterHealthResponse healthResponse = mock(ClusterHealthResponse.class);
        when(healthResponse.getStatus()).thenReturn(ClusterHealthStatus.GREEN);
        when(elasticsearchClient.cluster()).thenReturn(clusterClient);
        when(clusterClient.health(any(ClusterHealthRequest.class), any(RequestOptions.class))).thenReturn(healthResponse);

        ApiResponse response = healthService.checkHealthStatus();

        assertNotNull(response);
        assertTrue((Boolean) response.get(Constants.HEALTHY));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) response.get(Constants.CHECKS);
        assertEquals(4, checks.size());

        for (Map<String, Object> check : checks) {
            assertTrue((Boolean) check.get(Constants.HEALTHY));
        }
    }

    @Test
    void checkHealthStatus_ElasticsearchUnhealthy() throws Exception {
        List<Map<String, Object>> cassandraResponse = new ArrayList<>();
        cassandraResponse.add(new HashMap<>());

        when(cassandraOperation.getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_SYSTEM_SETTINGS,
                null,
                null,
                null))
                .thenReturn(cassandraResponse);

        when(redisCacheService.isRedisHealthy()).thenReturn(true);

        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(1);

        ClusterHealthResponse healthResponse = mock(ClusterHealthResponse.class);
        when(healthResponse.getStatus()).thenReturn(ClusterHealthStatus.RED);
        when(elasticsearchClient.cluster()).thenReturn(clusterClient);
        when(clusterClient.health(any(ClusterHealthRequest.class), any(RequestOptions.class))).thenReturn(healthResponse);

        ApiResponse response = healthService.checkHealthStatus();

        assertNotNull(response);
        assertFalse((Boolean) response.get(Constants.HEALTHY));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) response.get(Constants.CHECKS);

        Map<String, Object> esCheck = checks.stream()
                .filter(check -> "elasticsearch client".equals(check.get(Constants.NAME)))
                .findFirst()
                .orElse(null);

        assertNotNull(esCheck);
        assertFalse((Boolean) esCheck.get(Constants.HEALTHY));
    }

    @Test
    void checkHealthStatus_ElasticsearchException() throws Exception {
        List<Map<String, Object>> cassandraResponse = new ArrayList<>();
        cassandraResponse.add(new HashMap<>());

        when(cassandraOperation.getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_SYSTEM_SETTINGS,
                null,
                null,
                null))
                .thenReturn(cassandraResponse);

        when(redisCacheService.isRedisHealthy()).thenReturn(true);

        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(1);

        when(elasticsearchClient.cluster()).thenReturn(clusterClient);
        when(clusterClient.health(any(ClusterHealthRequest.class), any(RequestOptions.class))).thenThrow(new RuntimeException("Elasticsearch connection failed"));

        ApiResponse response = healthService.checkHealthStatus();

        assertNotNull(response);
        assertFalse((Boolean) response.get(Constants.HEALTHY));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) response.get(Constants.CHECKS);

        Map<String, Object> esCheck = checks.stream()
                .filter(check -> "elasticsearch client".equals(check.get(Constants.NAME)))
                .findFirst()
                .orElse(null);

        assertNotNull(esCheck);
        assertFalse((Boolean) esCheck.get(Constants.HEALTHY));
    }

    @Test
    void checkHealthStatus_ExceptionHandling() throws Exception {
        when(cassandraOperation.getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_SYSTEM_SETTINGS,
                null,
                null,
                null))
                .thenThrow(new RuntimeException("Cassandra connection failed"));

        ApiResponse response = healthService.checkHealthStatus();

        assertNotNull(response);
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Cassandra connection failed", response.getParams().getErr());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }
}



