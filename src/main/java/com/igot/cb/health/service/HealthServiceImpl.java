package com.igot.cb.health.service;

import com.igot.cb.exceptions.CustomException;
import com.igot.cb.transactional.elasticsearch.service.EsClientService;
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

    @Autowired
    EsClientService esClientService;

    private Logger log = LoggerFactory.getLogger(getClass().getName());

    @Override
    public ApiResponse checkHealthStatus(String requestId) throws Exception {
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_HEALTH_CHECK);
        Map<String, Object> responseObj = new HashMap<>();
        response.getParams().setMsgId(requestId);
        response.getParams().setResMsgId(requestId);
        try {

            List<Map<String, Object>> healthResults = new ArrayList<>();
            cassandraHealthStatus(healthResults);
            redisHealthStatus(healthResults);
            postgresHealthStatus(healthResults);
            elasticsearchHealthStatus(healthResults);

            responseObj.put(Constants.CHECKS, healthResults);
            responseObj.put(Constants.NAME,Constants.ALL_HEALTH_CHECK);
            responseObj.put(Constants.HEALTHY, Constants.TRUE);
            response.put(Constants.RESPONSE, responseObj);

        } catch (Exception e) {
            log.error("Failed to process health check. Exception: ", e);
            response.put(Constants.HEALTHY, false);
            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErr(e.getMessage());
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    public void cassandraHealthStatus(List<Map<String, Object>> response) throws Exception {
        Map<String, Object> result = ProjectUtil.createDefaultMapResponse(Constants.CASSANDRA_DB, null, null);
        try {
            List<Map<String, Object>> cassandraQueryResponse = cassandraOperation.getRecordsByPropertiesByKey(
                    Constants.KEYSPACE_SUNBIRD, Constants.TABLE_SYSTEM_SETTINGS, null, null, null);
            if (cassandraQueryResponse.isEmpty()) {
                setErrorDetails( result, new CustomException(Constants.CASSANDRA_DB +" Down", "Cassandra query returned empty result",
                        HttpStatus.SERVICE_UNAVAILABLE));
            }
        } catch (Exception e) {
            setErrorDetails( result, new CustomException(Constants.CASSANDRA_DB +" Down", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR));
        }
        response.add(result);
    }

    private void redisHealthStatus(List<Map<String, Object>> response) {

        Map<String, Object> result = ProjectUtil.createDefaultMapResponse(Constants.REDIS_CACHE,null,null);
        boolean isHealthy = true;

        try{
            isHealthy = redisCacheService.isRedisHealthy();

            if (!isHealthy) {
                setErrorDetails( result, new CustomException(Constants.REDIS_CACHE +" Down", "Redis is unhealthy",
                        HttpStatus.SERVICE_UNAVAILABLE));
            }
        }catch (Exception e) {
            setErrorDetails( result, new CustomException(Constants.REDIS_CACHE +" Down", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR));
        }

        response.add(result);


    }

    @Transactional(readOnly = true)
    public void postgresHealthStatus(List<Map<String, Object>> response) {
        Map<String, Object> result = ProjectUtil.createDefaultMapResponse(Constants.POSTGRES_DB,null,null);
        try {
            entityManager.createNativeQuery("SELECT 1").getSingleResult();
        } catch (Exception e) {
            setErrorDetails( result, new CustomException(Constants.POSTGRES_DB +" Down", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR));
        }
        response.add(result);

    }


    private void elasticsearchHealthStatus(List<Map<String, Object>> response) {

        Map<String, Object> result = ProjectUtil.createDefaultMapResponse(Constants.ELASTIC_SEARCH,null,null);
        boolean isHealthy = true;
        try {
            isHealthy = esClientService.isElasticsearchHealthy();

            if (!isHealthy) {

                setErrorDetails( result, new CustomException(Constants.ELASTIC_SEARCH +" Down", "Elasticsearch service is unhealthy",
                        HttpStatus.SERVICE_UNAVAILABLE));
            }

        }catch (Exception e) {
            setErrorDetails( result, new CustomException(Constants.ELASTIC_SEARCH +" Down", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR));
        }
        response.add(result);
    }

    private void setErrorDetails(Map<String, Object> response, CustomException e) {

        response.put(Constants.HEALTHY,Constants.FALSE);
        response.put(Constants.ERR, e.getHttpStatusCode().value());
        response.put(Constants.ERROR_MESSAGE, e.getMessage()!=null ? e.getMessage() : e.getHttpStatusCode().getReasonPhrase());
    }


}

