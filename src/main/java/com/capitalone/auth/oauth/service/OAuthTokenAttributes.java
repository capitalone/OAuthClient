package com.capitalone.auth.oauth.service;

import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;

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
public class OAuthTokenAttributes {

    private OAuthToken token;
    private Lock lock;
    private Future<OAuthToken> job;

    private OAuthTokenAttributes(Builder builder) {
        setToken(builder.token);
        lock = builder.lock;
        job = builder.job;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public OAuthToken getToken() {
        return token;
    }

    public void setToken(OAuthToken token) {
        this.token = token;
    }

    public Lock getLock() {
        return lock;
    }

    public Future<OAuthToken> getJob() {
        return job;
    }

    public void setJob(Future<OAuthToken> job) {
        this.job = job;
    }

    public void clearJob() {
        job = null;
    }

    public static final class Builder {
        private OAuthToken token;
        private Lock lock;
        private Future<OAuthToken> job;

        private Builder() {
        }

        public OAuthTokenAttributes build() {
            return new OAuthTokenAttributes(this);
        }

        public Builder token(OAuthToken val) {
            token = val;
            return this;
        }

        public Builder lock(Lock val) {
            lock = val;
            return this;
        }

        public Builder job(Future<OAuthToken> val) {
            job = val;
            return this;
        }
    }
}
