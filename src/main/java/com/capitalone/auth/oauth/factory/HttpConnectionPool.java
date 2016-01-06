package com.capitalone.auth.oauth.factory;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.HttpClients;

/**
 * Copyright [2016] Capital One Services, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
public class HttpConnectionPool {

    private HttpClientConnectionManager connectionManager;
    private RequestConfig requestConfig;


    public HttpConnectionPool(HttpClientConnectionManager manager, HttpConnectionConfig config) {
        this.connectionManager = manager;
        this.requestConfig = RequestConfig.custom()
                .setConnectTimeout(config.getHttpConnectionTimeout())
                .setSocketTimeout(config.getHttpSocketTimeout())
                .build();
    }

    public HttpClient getHttpClient() {
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HttpConnectionPool)) {
            return false;
        }

        HttpConnectionPool that = (HttpConnectionPool) o;

        if (connectionManager != null ? !connectionManager.equals(that.connectionManager) : that.connectionManager != null) {
            return false;
        }
        if (requestConfig != null ? !requestConfig.equals(that.requestConfig) : that.requestConfig != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = connectionManager != null ? connectionManager.hashCode() : 0;
        result = 31 * result + (requestConfig != null ? requestConfig.hashCode() : 0);
        return result;
    }
}
