package com.igot.cb.health;

import com.igot.cb.util.ApiResponse;


public interface HealthService {

    ApiResponse checkHealthStatus() throws Exception;

}
