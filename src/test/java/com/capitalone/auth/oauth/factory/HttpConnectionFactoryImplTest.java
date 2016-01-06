package com.capitalone.auth.oauth.factory;

import com.capitalone.auth.oauth.exceptions.LockInterruptedException;
import com.capitalone.auth.oauth.exceptions.SSLContextException;
import junit.framework.TestCase;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

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
public class HttpConnectionFactoryImplTest {

    @Test
    public void testGetConnectionClient() throws Exception {
        HttpConnectionFactoryImpl testee = new HttpConnectionFactoryImpl();

        final HttpConnectionPool connectionPool = testee.getConnectionPool(HttpConnectionConfig.newBuilder().httpConnectionTimeout(60).httpSocketTimeout(60).maxHttpConnections(20).build());
        assertThat(connectionPool, is(notNullValue()));

        // now again should return the same instance
        final HttpConnectionPool connectionPool2 = testee.getConnectionPool(HttpConnectionConfig.newBuilder().httpConnectionTimeout(60).httpSocketTimeout(60).maxHttpConnections(20).build());
        assertThat(connectionPool2, is(sameInstance(connectionPool)));

        // it should give a new connection pool if the config params are different
        final HttpConnectionPool connectionPool3 = testee.getConnectionPool(HttpConnectionConfig.newBuilder().httpConnectionTimeout(120).httpSocketTimeout(60).maxHttpConnections(20).build());
        assertThat(connectionPool3, is(not(sameInstance(connectionPool))));

        // test with specific SSL protocol version
        final HttpConnectionPool connectionPool4 = testee.getConnectionPool(
                HttpConnectionConfig.newBuilder().httpConnectionTimeout(120).httpSocketTimeout(60).maxHttpConnections(20).sslProtocol("TLSv1.2").build());
        assertThat(connectionPool4, is(notNullValue()));
    }

    @Test (expected = SSLContextException.class)
    public void shouldThrowSSLContextExceptionIfSSLProtocolIsInvalid() {
        HttpConnectionFactoryImpl httpConnectionFactoryImpl = new HttpConnectionFactoryImpl();

        httpConnectionFactoryImpl.getConnectionPool(
                HttpConnectionConfig.newBuilder().httpConnectionTimeout(60).httpSocketTimeout(60).maxHttpConnections(20).sslProtocol("INVALID_PROTOCOL").build());
    }

    @Test
    public void testUsesLockForThreadSafety() throws Exception {
        Lock mockLock = mock(Lock.class);
        HttpConnectionFactoryImpl testee = new HttpConnectionFactoryImpl(mockLock);

        testee.getConnectionPool(HttpConnectionConfig.newBuilder().httpConnectionTimeout(120).httpSocketTimeout(60).maxHttpConnections(20).build());

        verify(mockLock).tryLock(eq(60L), eq(TimeUnit.SECONDS));
        verify(mockLock).unlock();
    }

    @Test
    public void testUnlockEvenIfItThrows() throws Exception {
        Lock mockLock = mock(Lock.class);
        HttpConnectionFactoryImpl testee = new HttpConnectionFactoryImpl(mockLock);

        when(mockLock.tryLock(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException("something"));

        try {
            testee.getConnectionPool(HttpConnectionConfig.newBuilder().httpConnectionTimeout(120).httpSocketTimeout(60).maxHttpConnections(20).build());
            TestCase.fail("should have thrown InterruptedException");
        } catch (LockInterruptedException e) {
            verify(mockLock).unlock();
        }
    }
}
