package com.capitalone.auth.example;

import com.capitalone.auth.ClientCredentialsProvider;
import com.capitalone.auth.TokenService;
import com.capitalone.auth.oauth.factory.HttpConnectionConfig;
import com.capitalone.auth.oauth.factory.HttpConnectionFactory;
import com.capitalone.auth.oauth.factory.HttpConnectionFactoryImpl;
import com.capitalone.auth.oauth.framework.OAuthClientCredentials;
import com.capitalone.auth.oauth.framework.OAuthClientCredentialsProvider;
import com.capitalone.auth.oauth.service.ClientSecretException;
import com.capitalone.auth.oauth.service.ClientSecretService;
import com.capitalone.auth.oauth.service.OAuthToken;
import com.capitalone.auth.oauth.service.OAuthTokenService;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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
public class ExampleClient {

    private final TokenService oAuthTokenService;

    public static void main(String[] args) {
        ExampleClient client = new ExampleClient();
        client.makeRequest();
    }

    public ExampleClient() {
        URI authServerURI = null;

        try {
            authServerURI = new URI("https://myauthserver.com/oauth/oauth20/token");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        OAuthClientCredentials builtClientCredentials = OAuthClientCredentials.newBuilder()
                .clientId("my_client_id")
                .clientSecret("my_client_secret")
                .clientSecretEncryptionKey(null)
                .clientURIRegex(".*")
                .grantType("client_credentials")
                .authServerURI(authServerURI)
                .build();

        ClientCredentialsProvider<OAuthClientCredentials> clientCredentialsProvider = new OAuthClientCredentialsProvider(builtClientCredentials);

        ClientSecretService clientSecretService = new DummyClientSecretService();

        HttpConnectionFactory httpConnectionFactory = new HttpConnectionFactoryImpl(new ReentrantLock());

        HttpConnectionConfig httpConnectionConfig = HttpConnectionConfig.newBuilder()
                .httpConnectionTimeout(60000)
                .httpSocketTimeout(40000)
                .maxHttpConnections(20)
                .build();

        oAuthTokenService = new OAuthTokenService(httpConnectionFactory, httpConnectionConfig, 20, 10, clientCredentialsProvider, clientSecretService);
    }

    public void makeRequest() {
        try {
            OAuthToken token = (OAuthToken) oAuthTokenService.obtainTokenFor(new URI("https://myauthserver.com/protected/endpoint"));
            System.out.println(token.getValue());
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private class DummyClientSecretService implements ClientSecretService {
        @Override
        public String obtainClientSecret(OAuthClientCredentials clientCredentials) throws ClientSecretException {
            if (null == clientCredentials.getClientSecretEncryptionKey()) {
                return clientCredentials.getClientSecret();
            } else {
                return decryptSecret(clientCredentials.getClientSecretEncryptionKey(), clientCredentials.getClientSecret());
            }
        }

        private String decryptSecret(String encryptionKey, String encryptedClientSecret) {
            return "abc1";
        }
    }
}
