package com.capitalone.auth.oauth.framework;

import java.net.URI;

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
public class OAuthClientCredentials implements Cloneable {

    private String grantType;
    private String clientId;
    private String clientSecret;
    private URI authServerURI;
    private String clientURIRegex;
    private String clientSecretEncryptionKey;

    private OAuthClientCredentials(Builder builder) {
        grantType = builder.grantType;
        clientId = builder.clientId;
        clientSecret = builder.clientSecret;
        authServerURI = builder.authServerURI;
        clientURIRegex = builder.clientURIRegex;
        clientSecretEncryptionKey = builder.clientSecretEncryptionKey;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public String getGrantType() {
        return grantType;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public URI getAuthServerURI() {
        return authServerURI;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        OAuthClientCredentials that = (OAuthClientCredentials) o;

        if (grantType != null ? !grantType.equals(that.grantType) : that.grantType != null) {
            return false;
        }

        if (clientId != null ? !clientId.equals(that.clientId) : that.clientId != null) {
            return false;
        }

        if (clientSecret != null ? !clientSecret.equals(that.clientSecret) : that.clientSecret != null) {
            return false;
        }

        return authServerURI != null ? authServerURI.equals(that.authServerURI) : that.authServerURI == null;

    }

    @Override
    public int hashCode() {
        int result = grantType != null ? grantType.hashCode() : 0;
        result = 31 * result + (clientId != null ? clientId.hashCode() : 0);
        result = 31 * result + (clientSecret != null ? clientSecret.hashCode() : 0);
        result = 31 * result + (authServerURI != null ? authServerURI.hashCode() : 0);
        return result;
    }

    @Override
    public OAuthClientCredentials clone() {
        return OAuthClientCredentials.newBuilder()
                .grantType(grantType)
                .clientURIRegex(clientURIRegex)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .authServerURI(authServerURI)
                .clientSecretEncryptionKey(clientSecretEncryptionKey)
                .build();
    }

    public String getClientURIRegex() {
        return clientURIRegex;
    }

    public String getClientSecretEncryptionKey() {
        return clientSecretEncryptionKey;
    }

    public static final class Builder {
        private String grantType;
        private String clientId;
        private String clientSecret;
        private URI authServerURI;
        private String clientURIRegex;
        private String clientSecretEncryptionKey;

        private Builder() {
        }

        public Builder grantType(String val) {
            grantType = val;
            return this;
        }

        public Builder clientId(String val) {
            clientId = val;
            return this;
        }

        public Builder clientSecret(String val) {
            clientSecret = val;
            return this;
        }

        public Builder authServerURI(URI val) {
            authServerURI = val;
            return this;
        }

        public Builder clientURIRegex(String val) {
            clientURIRegex = val;
            return this;
        }

        public Builder clientSecretEncryptionKey(String val) {
            clientSecretEncryptionKey = val;
            return this;
        }


        public OAuthClientCredentials build() {
            return new OAuthClientCredentials(this);
        }
    }
}
