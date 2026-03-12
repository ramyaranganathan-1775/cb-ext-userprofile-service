package com.igot.cb.health.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
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

    @BeforeEach
    void setUp() {
        // Setup is handled by MockitoExtension
    }

    // ==================== Test: All Services Healthy ====================

    @Test
    void testCheckHealthStatus_AllServicesHealthy() throws Exception {
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
        assertTrue((Boolean) response.get(Constants.HEALTHY));
        assertNotNull(response.get(Constants.CHECKS));
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertEquals(HttpStatus.OK, response.getResponseCode());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) response.get(Constants.CHECKS);
        assertEquals(3, checks.size());

        // Verify all checks are healthy
        checks.forEach(check -> assertTrue((Boolean) check.get(Constants.HEALTHY)));
    }

    // ==================== Test: Cassandra Unhealthy ====================

    @Test
    void testCheckHealthStatus_CassandraUnhealthy() throws Exception {
        // Arrange
        when(cassandraOperation.getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_SYSTEM_SETTINGS,
                null,
                null,
                null))
                .thenReturn(new ArrayList<>());

        when(redisCacheService.isRedisHealthy()).thenReturn(true);

        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(1);

        // Act
        ApiResponse response = healthService.checkHealthStatus();

        // Assert
        assertNotNull(response);
        assertFalse((Boolean) response.get(Constants.HEALTHY));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) response.get(Constants.CHECKS);
        assertEquals(3, checks.size());

        // Verify Cassandra check is unhealthy
        Map<String, Object> cassandraCheck = checks.stream()
                .filter(check -> Constants.CASSANDRA_DB.equals(check.get(Constants.NAME)))
                .findFirst()
                .orElse(null);

        assertNotNull(cassandraCheck);
        assertFalse((Boolean) cassandraCheck.get(Constants.HEALTHY));
    }

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

    @Test
    void testCheckHealthStatus_PostgresUnhealthy() throws Exception {
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
        when(nativeQuery.getSingleResult()).thenThrow(new RuntimeException("Database connection failed"));

        // Act
        ApiResponse response = healthService.checkHealthStatus();

        // Assert
        assertNotNull(response);
        assertFalse((Boolean) response.get(Constants.HEALTHY));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) response.get(Constants.CHECKS);
        assertEquals(3, checks.size());
    }

    // ==================== Test: All Services Unhealthy ====================

    @Test
    void testCheckHealthStatus_AllServicesUnhealthy() throws Exception {
        // Arrange
        when(cassandraOperation.getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_SYSTEM_SETTINGS,
                null,
                null,
                null))
                .thenReturn(new ArrayList<>());

        when(redisCacheService.isRedisHealthy()).thenReturn(false);

        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenThrow(new RuntimeException("Database error"));

        // Act
        ApiResponse response = healthService.checkHealthStatus();

        // Assert
        assertNotNull(response);
        assertFalse((Boolean) response.get(Constants.HEALTHY));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) response.get(Constants.CHECKS);
        assertEquals(3, checks.size());

        // All checks should be unhealthy
        checks.forEach(check -> assertFalse((Boolean) check.get(Constants.HEALTHY)));
    }

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

    @Test
    void testCheckHealthStatus_CassandraAndRedisUnhealthy() throws Exception {
        // Arrange
        when(cassandraOperation.getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_SYSTEM_SETTINGS,
                null,
                null,
                null))
                .thenReturn(new ArrayList<>());

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
        assertEquals(3, checks.size());

        // Count unhealthy checks
        long unhealthyCount = checks.stream()
                .filter(check -> !(Boolean) check.get(Constants.HEALTHY))
                .count();
        assertEquals(2, unhealthyCount);
    }

    @Test
    void testCheckHealthStatus_OnlyPostgresUnhealthy() throws Exception {
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
        when(nativeQuery.getSingleResult()).thenThrow(new RuntimeException("Connection pool exhausted"));

        // Act
        ApiResponse response = healthService.checkHealthStatus();

        // Assert
        assertNotNull(response);
        assertFalse((Boolean) response.get(Constants.HEALTHY));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) response.get(Constants.CHECKS);

        // Verify only Postgres is unhealthy
        long unhealthyCount = checks.stream()
                .filter(check -> !(Boolean) check.get(Constants.HEALTHY))
                .count();
        assertEquals(1, unhealthyCount);
    }

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
}

