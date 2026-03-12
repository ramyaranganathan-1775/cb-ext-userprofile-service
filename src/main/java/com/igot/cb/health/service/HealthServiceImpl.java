package com.igot.cb.health;

import jakarta.persistence.EntityManager;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class HealthServiceImpl implements HealthService {


    @Autowired
    CassandraOperation cassandraOperation;

    @Autowired
    CacheService redisCacheService;

    @Autowired
    EntityManager entityManager;

    @Qualifier("igotESClient")
    RestHighLevelClient igotESClient;

    private Logger log = LoggerFactory.getLogger(getClass().getName());

    @Override
    public ApiResponse checkHealthStatus() throws Exception {
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_HEALTH_CHECK);
        try {
            response.put(Constants.HEALTHY, true);
            List<Map<String, Object>> healthResults = new ArrayList<>();
            response.put(Constants.CHECKS, healthResults);
            cassandraHealthStatus(response);
            redisHealthStatus(response);
            postgresHealthStatus(response);
            elasticsearchHealthStatus(response);
        } catch (Exception e) {
            log.error("Failed to process health check. Exception: ", e);
            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErr(e.getMessage());
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    public void cassandraHealthStatus(ApiResponse response) throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.NAME, Constants.CASSANDRA_DB);
        Boolean res = true;
        List<Map<String, Object>> cassandraQueryResponse = cassandraOperation.getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD, Constants.TABLE_SYSTEM_SETTINGS, null, null,null);
        if (cassandraQueryResponse.isEmpty()) {
            res = false;
            response.put(Constants.HEALTHY, res);
        }
        result.put(Constants.HEALTHY, res);
        ((List<Map<String, Object>>) response.get(Constants.CHECKS)).add(result);
    }

    private void redisHealthStatus(ApiResponse response) {

        Map<String, Object> result = new HashMap<>();
        result.put(Constants.NAME, Constants.REDIS_CACHE);

        boolean isHealthy = redisCacheService.isRedisHealthy();

        result.put(Constants.HEALTHY, isHealthy);

        ((List<Map<String, Object>>) response.get(Constants.CHECKS)).add(result);

        if (!isHealthy) {
            response.put(Constants.HEALTHY, false);
        }
    }

    @Transactional(readOnly = true)
    public void postgresHealthStatus(ApiResponse response) {
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.NAME, Constants.CASSANDRA_DB);
        Boolean res = true;
        try {
            entityManager.createNativeQuery("SELECT 1").getSingleResult();
            result.put(Constants.HEALTHY, res);
            ((List<Map<String, Object>>) response.get(Constants.CHECKS)).add(result);
        } catch (Exception e) {
            res = false;
            response.put(Constants.HEALTHY, res);
            result.put(Constants.HEALTHY, res);
            ((List<Map<String, Object>>) response.get(Constants.CHECKS)).add(result);
        }
    }


    private void elasticsearchHealthStatus(ApiResponse response) {
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.NAME, "elasticsearch client");
        Boolean res = true;
        try {
            ClusterHealthRequest request = new ClusterHealthRequest();
            ClusterHealthResponse healthResponse = igotESClient.cluster().health(request, RequestOptions.DEFAULT);

            // Check if cluster is healthy (green or yellow status)
            String clusterStatus = healthResponse.getStatus().toString().toLowerCase();
            if (!"green".equals(clusterStatus) && !"yellow".equals(clusterStatus)) {
                res = false;
                response.put(Constants.HEALTHY, res);
            }

            result.put(Constants.HEALTHY, res);
            ((List<Map<String, Object>>) response.get(Constants.CHECKS)).add(result);
        } catch (Exception e) {
            // Handle connection failures
            res = false;
            response.put(Constants.HEALTHY, res);
            result.put(Constants.HEALTHY, res);
            ((List<Map<String, Object>>) response.get(Constants.CHECKS)).add(result);
        }
    }

}

