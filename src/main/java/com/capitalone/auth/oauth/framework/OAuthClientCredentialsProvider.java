package com.capitalone.auth.oauth.framework;

import com.capitalone.auth.ClientCredentialsProvider;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

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
public class OAuthClientCredentialsProvider implements ClientCredentialsProvider<OAuthClientCredentials> {

    private List<OAuthClientCredentials> clientCredentialsList = new ArrayList<>();

    public OAuthClientCredentialsProvider(OAuthClientCredentials[] clientCredentialsList) {
        setClientCredentialsList(clientCredentialsList);
    }

    public OAuthClientCredentialsProvider(OAuthClientCredentials clientCredentials) {
        this.clientCredentialsList.add(clientCredentials.clone());
    }

    @Override
    public OAuthClientCredentials getClientCredentialsFor(URI uri) throws ClientCredentialsNotFoundException {
        for (OAuthClientCredentials clientCredentials : clientCredentialsList) {
            if (uri.toString().matches(clientCredentials.getClientURIRegex())) {
                return clientCredentials;
            }
        }

        throw new ClientCredentialsNotFoundException("client credentials not found");
    }

    private void setClientCredentialsList(final OAuthClientCredentials[] incomingClientCredentialsList) {
        for (OAuthClientCredentials incomingClientCredentials : incomingClientCredentialsList) {
            this.clientCredentialsList.add(incomingClientCredentials.clone());
        }
    }
}
