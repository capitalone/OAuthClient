package com.capitalone.auth.oauth.factory;

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
public class HttpConnectionConfig {
    private static final String DEFAULT_SSL_PROTOCOL = "TLSv1.2";

    private final Integer httpConnectionTimeout;
    private final Integer httpSocketTimeout;
    private final Integer maxHttpConnections;
    private final String sslProtocol;

    private HttpConnectionConfig(final Builder builder) {
        this.httpConnectionTimeout = builder.httpConnectionTimeout;
        this.httpSocketTimeout = builder.httpSocketTimeout;
        this.maxHttpConnections = builder.maxHttpConnections;
        this.sslProtocol = builder.sslProtocol;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public Integer getHttpConnectionTimeout() {
        return httpConnectionTimeout;
    }

    public Integer getHttpSocketTimeout() {
        return httpSocketTimeout;
    }

    public Integer getMaxHttpConnections() {
        return maxHttpConnections;
    }

    public String getSslProtocol() {
        return sslProtocol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        HttpConnectionConfig that = (HttpConnectionConfig) o;

        if (httpConnectionTimeout != null ? !httpConnectionTimeout.equals(that.httpConnectionTimeout) : that.httpConnectionTimeout != null) {
            return false;
        }
        if (httpSocketTimeout != null ? !httpSocketTimeout.equals(that.httpSocketTimeout) : that.httpSocketTimeout != null) {
            return false;
        }
        if (maxHttpConnections != null ? !maxHttpConnections.equals(that.maxHttpConnections) : that.maxHttpConnections != null) {
            return false;
        }
        if (sslProtocol != null ? !sslProtocol.equals(that.sslProtocol) : that.sslProtocol != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = httpConnectionTimeout != null ? httpConnectionTimeout.hashCode() : 0;
        result = 31 * result + (httpSocketTimeout != null ? httpSocketTimeout.hashCode() : 0);
        result = 31 * result + (maxHttpConnections != null ? maxHttpConnections.hashCode() : 0);
        result = 31 * result + (sslProtocol != null ? sslProtocol.hashCode() : 0);
        return result;
    }

    public static final class Builder {
        private Integer httpConnectionTimeout;
        private Integer httpSocketTimeout;
        private Integer maxHttpConnections;
        private String sslProtocol = DEFAULT_SSL_PROTOCOL;

        private Builder() {
        }

        public Builder httpConnectionTimeout(Integer val) {
            httpConnectionTimeout = val;
            return this;
        }

        public Builder httpSocketTimeout(Integer val) {
            httpSocketTimeout = val;
            return this;
        }

        public Builder maxHttpConnections(Integer val) {
            maxHttpConnections = val;
            return this;
        }

        public Builder sslProtocol(String sslProtocol) {
            this.sslProtocol = sslProtocol;
            return this;
        }

        public HttpConnectionConfig build() {
            return new HttpConnectionConfig(this);
        }
    }
}
