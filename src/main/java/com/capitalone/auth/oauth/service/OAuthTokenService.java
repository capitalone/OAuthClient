package com.capitalone.auth.oauth.service;

import com.capitalone.auth.ClientCredentialsProvider;
import com.capitalone.auth.Token;
import com.capitalone.auth.TokenService;
import com.capitalone.auth.oauth.factory.HttpConnectionConfig;
import com.capitalone.auth.oauth.factory.HttpConnectionFactory;
import com.capitalone.auth.oauth.factory.HttpConnectionPool;
import com.capitalone.auth.oauth.framework.ClientCredentialsNotFoundException;
import com.capitalone.auth.oauth.framework.OAuthClientCredentials;
import com.capitalone.auth.oauth.framework.protocol.ServerOAuthToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p/>
 * This manages oauth tokens. If a token is close to expiry, it will prefetch (on request).
 * <p/>
 * You just ask it for a token for the given uri (client uri) and it will work out which oauth server it will use
 * and manages locks etc for that service.
 *
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
public class OAuthTokenService implements TokenService {

    public static final String KEY_GRANT_TYPE = "grant_type";
    public static final String KEY_CLIENT_ID = "client_id";
    public static final String KEY_CLIENT_SECRET = "client_secret";

    private ObjectMapper objectMapper = new ObjectMapper();
    private ClientCredentialsProvider<OAuthClientCredentials> clientCredentialsProvider;
    private int prefetchTimeout;
    private ClientSecretService clientSecretService;
    private Lock lock = new ReentrantLock();
    private HttpConnectionPool httpConnectionPool;
    private Map<OAuthClientCredentials, OAuthTokenAttributes> tokenCache = new HashMap<>();
    private ExecutorService executorService;

    /**
     * Creates an oauth token service that is responsible for managing oauth tokens.
     *
     * @param httpConnectionFactory the http connection factory to use for getting oauth tokens
     * @param prefetchTimeout       the prefetch buffer (in milliseconds)
     */
    public OAuthTokenService(HttpConnectionFactory httpConnectionFactory, HttpConnectionConfig httpConnectionConfig, int prefetchPoolSize,
                             int prefetchTimeout, ClientCredentialsProvider<OAuthClientCredentials> oAuthClientCredentialsProvider, ClientSecretService clientSecretService) {
        this.prefetchTimeout = prefetchTimeout;
        this.clientSecretService = clientSecretService;
        this.httpConnectionPool = httpConnectionFactory.getConnectionPool(httpConnectionConfig);
        this.executorService = Executors.newFixedThreadPool(prefetchPoolSize);
        this.clientCredentialsProvider = oAuthClientCredentialsProvider;
    }

    @Override
    public Token obtainTokenFor(URI uri) throws IOException {
        final OAuthClientCredentials clientCredentials;
        try {
            clientCredentials = clientCredentialsProvider.getClientCredentialsFor(uri);
        } catch (ClientCredentialsNotFoundException e) {
            throw new IOException("oauth configuration not found for uri", e);
        }

        OAuthTokenAttributes oauthTokenAttributes = null;

        // global lock here - manage the individual locks. must be locked globally to avoid creating
        // multiple lock objects for the same url.
        try {
            if (lock.tryLock(10, TimeUnit.SECONDS)) {
                if (tokenCache.containsKey(clientCredentials)) {
                    oauthTokenAttributes = tokenCache.get(clientCredentials);
                } else {
                    oauthTokenAttributes = OAuthTokenAttributes.newBuilder()
                            .token(null)
                            .lock(new ReentrantLock())
                            .build();

                    tokenCache.put(clientCredentials, oauthTokenAttributes);
                }
            } else {
                throw new IOException("failed to acquire global lock in time");
            }
        } catch (InterruptedException e) {
            throw new IOException("error acquiring global lock", e);
        } finally {
            lock.unlock();
        }

        // now get the lock for the individual oauth server
        Lock oauthTokenAttributesLock = oauthTokenAttributes.getLock();
        try {
            // lock it with a timeout
            if (oauthTokenAttributesLock.tryLock(10, TimeUnit.SECONDS)) {

                // check to see if the token has expired
                OAuthToken token = oauthTokenAttributes.getToken();
                if (token == null || token.hasExpired()) {

                    // it has expired, so check if we have a prefetch job in progress, if so wait for it
                    final Future<OAuthToken> inFlightJob = oauthTokenAttributes.getJob();

                    if (null != inFlightJob) {
                        token = inFlightJob.get();
                        oauthTokenAttributes.clearJob();
                    }

                    // token could have been replaced by prefetch job, so check to make sure that it has not expired
                    if (null == token || token.hasExpired()) {
                        final OAuthTokenRequestTask oauthTokenRequestTask = new OAuthTokenRequestTask(clientCredentials, httpConnectionPool, objectMapper, clientSecretService);
                        token = oauthTokenRequestTask.call();
                    }
                }

                // now set the valid token
                oauthTokenAttributes.setToken(token);

                // and if we are close to expiry, start a job to get it
                if (this.prefetchTimeout > token.getRemainingTime() && null == oauthTokenAttributes.getJob()) {
                    final OAuthTokenRequestTask oauthTokenRequestTask = new OAuthTokenRequestTask(clientCredentials, httpConnectionPool, objectMapper, clientSecretService);
                    final Future<OAuthToken> job = executorService.submit(oauthTokenRequestTask);
                    oauthTokenAttributes.setJob(job);
                }

                return token;
            } else {
                throw new IOException("failed to acquire lock in time for " + clientCredentials.getAuthServerURI());
            }
        } catch (IOException e) {
            throw new IOException("Could not get authorisation from server", e);
        } catch (InterruptedException e) {
            throw new IOException("error acquiring lock for " + clientCredentials.getAuthServerURI(), e);
        } catch (ExecutionException e) {
            throw new IOException("error requesting oauth token", e);
        } catch (ClientSecretException e) {
            throw new IOException("error obtaining client secret", e);
        } finally {
            oauthTokenAttributesLock.unlock();
        }
    }

    ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ClientCredentialsProvider getClientCredentialsProvider() {
        return clientCredentialsProvider;
    }

    HttpConnectionPool getHttpConnectionPool() {
        return httpConnectionPool;
    }

    void setHttpConnectionPool(HttpConnectionPool httpConnectionPool) {
        this.httpConnectionPool = httpConnectionPool;
    }

    void putToken(OAuthClientCredentials clientCredentials, OAuthTokenAttributes oauthTokenAttributes) {
        this.tokenCache.put(clientCredentials, oauthTokenAttributes);
    }

    Map<OAuthClientCredentials, OAuthTokenAttributes> getTokenCache() {
        return tokenCache;
    }

    void setLock(Lock lock) {
        this.lock = lock;
    }

    ExecutorService getExecutorService() {
        return executorService;
    }

    void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    private static final class OAuthTokenRequestTask implements Callable<OAuthToken> {
        private OAuthClientCredentials clientCredentials;
        private HttpConnectionPool httpConnectionPool;
        private ObjectMapper objectMapper;
        private ClientSecretService clientSecretService;

        public OAuthTokenRequestTask(OAuthClientCredentials clientCredentials, HttpConnectionPool httpConnectionPool, ObjectMapper objectMapper, ClientSecretService clientSecretService) {
            this.clientCredentials = clientCredentials;
            this.httpConnectionPool = httpConnectionPool;
            this.objectMapper = objectMapper;
            this.clientSecretService = clientSecretService;
        }

        @Override
        public OAuthToken call() throws IOException, ClientSecretException {
            final HttpClient httpClient = this.httpConnectionPool.getHttpClient();

            final List<NameValuePair> urlParameters = new ArrayList<>();
            urlParameters.add(new BasicNameValuePair(KEY_CLIENT_ID, clientCredentials.getClientId()));

            String clientSecret = clientSecretService.obtainClientSecret(clientCredentials);

            urlParameters.add(new BasicNameValuePair(KEY_CLIENT_SECRET, clientSecret));
            urlParameters.add(new BasicNameValuePair(KEY_GRANT_TYPE, clientCredentials.getGrantType()));

            final HttpPost httpPost = new HttpPost(clientCredentials.getAuthServerURI());
            httpPost.setEntity(new UrlEncodedFormEntity(urlParameters));

            final HttpResponse httpResponse = httpClient.execute(httpPost);
            final HttpEntity entity = httpResponse.getEntity();
            final String content = IOUtils.toString(entity.getContent());
            final ServerOAuthToken serverToken = this.objectMapper.readValue(content, ServerOAuthToken.class);
            final OAuthToken token = OAuthToken.newBuilder()
                    .accessToken(serverToken.getAccessToken())
                    .tokenType(serverToken.getTokenType())
                    .expiresIn(serverToken.getExpiresIn())
                    .build();

            return token;
        }
    }
}
