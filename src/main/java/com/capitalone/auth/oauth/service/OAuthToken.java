package com.capitalone.auth.oauth.service;

import com.capitalone.auth.Token;

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
public class OAuthToken implements Token {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private long expiresOn;
    private long creationTime;

    public OAuthToken() {
        this.creationTime = System.currentTimeMillis();
    }

    private OAuthToken(Builder builder) {
        this();
        this.accessToken = builder.accessToken;
        this.tokenType = builder.tokenType;
        this.expiresIn = builder.expiresIn - 10;    // TODO: Needs to be configurable at some point.
        this.expiresOn = this.creationTime + (this.expiresIn * 1000);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    @Override
    public String getValue() {
        return accessToken;
    }

    public boolean hasExpired() {
        long currentTime = System.currentTimeMillis();
        return expiresOn <= currentTime;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getExpiresOn() {
        return expiresOn;
    }

    public long getRemainingTime() {
        long currentTime = System.currentTimeMillis();
        long expiresOn = this.expiresOn;
        return expiresOn - currentTime;
    }

    public String getTokenType() {
        return tokenType;
    }

    public static final class Builder {
        private String accessToken;
        private String tokenType;
        private long expiresIn;

        public Builder() {
        }

        public Builder accessToken(String val) {
            accessToken = val;
            return this;
        }

        public Builder tokenType(String val) {
            tokenType = val;
            return this;
        }

        public Builder expiresIn(long val) {
            expiresIn = val;
            return this;
        }

        public OAuthToken build() {
            return new OAuthToken(this);
        }
    }
}
