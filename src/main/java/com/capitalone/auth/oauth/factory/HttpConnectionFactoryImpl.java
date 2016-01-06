package com.capitalone.auth.oauth.factory;

import com.capitalone.auth.oauth.exceptions.LockInterruptedException;
import com.capitalone.auth.oauth.exceptions.SSLContextException;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
public class HttpConnectionFactoryImpl implements HttpConnectionFactory {

    private final Map<HttpConnectionConfig, HttpConnectionPool> connectionPools = new HashMap<>();
    private final Lock lock;

    public HttpConnectionFactoryImpl(Lock lock) {
        this.lock = lock;
    }

    public HttpConnectionFactoryImpl() {
        this(new ReentrantLock());
    }

    /**
     * Note this can return null if it is not able to obtain a lock for your connection pool
     *
     * @param connectionConfig the configuration
     * @return the connection pool
     */
    public HttpConnectionPool getConnectionPool(HttpConnectionConfig connectionConfig) {
        HttpConnectionPool pool = null;
        try {
            // use a re-entrant lock here as we can't easily track locks across the platform
            // a re-entrant lock will attempt to acquire a lock, unless it already holds it.
            // this should prepare the pool for reuse
            if (lock.tryLock(60, TimeUnit.SECONDS)) {
                if (connectionPools.containsKey(connectionConfig)) {
                    return connectionPools.get(connectionConfig);
                }
                pool = newConnectionPool(connectionConfig);

                connectionPools.put(connectionConfig, pool);
            }
        } catch (InterruptedException e) {
            throw new LockInterruptedException("Thread interrupted while attempting to acquire lock", e);
        } finally {
            lock.unlock();
        }
        return pool;
    }

    private HttpConnectionPool newConnectionPool(HttpConnectionConfig connectionConfig) {
        SSLContext sslContext;

        try {
            sslContext = SSLContexts.custom()
                    .useProtocol(connectionConfig.getSslProtocol())
                    .build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new SSLContextException(String.format("No such SSL protocol: %s", connectionConfig.getSslProtocol()), e);
        }
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", new SSLConnectionSocketFactory(sslContext))
                .build();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        connectionManager.setMaxTotal(connectionConfig.getMaxHttpConnections());

        return new HttpConnectionPool(connectionManager, connectionConfig);
    }
}
