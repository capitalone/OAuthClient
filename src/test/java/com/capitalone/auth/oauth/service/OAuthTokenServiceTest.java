package com.capitalone.auth.oauth.service;

import com.capitalone.auth.ClientCredentialsProvider;
import com.capitalone.auth.Token;
import com.capitalone.auth.oauth.factory.HttpConnectionConfig;
import com.capitalone.auth.oauth.factory.HttpConnectionFactory;
import com.capitalone.auth.oauth.factory.HttpConnectionPool;
import com.capitalone.auth.oauth.framework.ClientCredentialsNotFoundException;
import com.capitalone.auth.oauth.framework.OAuthClientCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import junit.framework.TestCase;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
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
public class OAuthTokenServiceTest {

    private OAuthTokenService testee;
    private HttpConnectionFactory mockFactory;
    private HttpConnectionPool mockPool;
    private ClientCredentialsProvider mockProvider;
    private Lock mockLock;
    private ClientSecretService mockClientSecretService;

    @Before
    public void setup() {
        mockPool = mock(HttpConnectionPool.class);
        mockFactory = mock(HttpConnectionFactory.class);
        when(mockFactory.getConnectionPool(eq(HttpConnectionConfig.newBuilder().httpConnectionTimeout(60).httpSocketTimeout(40).maxHttpConnections(20).build()))).thenReturn(mockPool);

        mockProvider = mock(ClientCredentialsProvider.class);

        HttpConnectionConfig httpConnectionConfig = HttpConnectionConfig.newBuilder()
                .httpConnectionTimeout(60)
                .httpSocketTimeout(40)
                .maxHttpConnections(20)
                .build();

        mockClientSecretService = mock(ClientSecretService.class);

        testee = new OAuthTokenService(mockFactory, httpConnectionConfig, 20, 20000, mockProvider, mockClientSecretService);

        testee.setHttpConnectionPool(mockPool);

        mockLock = mock(Lock.class);
    }

    @Test
    public void testConstructedWithAFixedSizeExecutorPool() throws Exception {

        HttpConnectionFactory mockConnectionFactory = mock(HttpConnectionFactory.class);

        HttpConnectionConfig httpConnectionConfig = HttpConnectionConfig.newBuilder()
                .httpConnectionTimeout(60)
                .httpSocketTimeout(40)
                .maxHttpConnections(20)
                .build();

        OAuthTokenService newInstance = new OAuthTokenService(mockConnectionFactory, httpConnectionConfig, 10, 10, mockProvider, mockClientSecretService);

        assertThat(newInstance.getExecutorService(), instanceOf(ThreadPoolExecutor.class));
    }

    @Test
    public void testObtainTokenFor_requestNewToken() throws Exception {
        testee.setObjectMapper(new ObjectMapper());

        final HttpClient mockClient = mock(HttpClient.class);
        when(mockPool.getHttpClient()).thenReturn(mockClient);

        final HttpResponse mockHttpResponse = mock(HttpResponse.class);
        when(mockClient.execute(any(HttpPost.class))).thenReturn(mockHttpResponse);

        final StatusLine mockStatusLine = mock(StatusLine.class);
        when(mockHttpResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(200);
        when(mockHttpResponse.getEntity()).thenReturn(new StringEntity("{\n" +
                "  \"access_token\": \"sparkpost-token\",\n" +
                "  \"token_type\": \"Bearer\",\n" +
                "  \"expires_in\": 60\n" +
                "}"));

        final OAuthClientCredentials clientCredentials = OAuthClientCredentials.newBuilder().clientId("xyz").clientSecret("abc").grantType("client_credentials").authServerURI(new URI("https://my.oauth.club/")).build();
        when(mockProvider.getClientCredentialsFor(any(URI.class))).thenReturn(clientCredentials);

        when(mockClientSecretService.obtainClientSecret(clientCredentials)).thenReturn("abc");

        final URI uri = new URI("https://my.service.to.be.authorised.com/");

        final Token token = testee.obtainTokenFor(uri);
        assertThat(token.getValue(), is("sparkpost-token"));

        final ArgumentCaptor<HttpPost> captor = ArgumentCaptor.forClass(HttpPost.class);
        verify(mockClient).execute(captor.capture());
        assertThat(captor.getValue().getURI(), is(equalTo(new URI("https://my.oauth.club/"))));
        assertThat(captor.getValue().getEntity(), is(notNullValue()));
        assertThat(captor.getValue().getEntity(), is(instanceOf(UrlEncodedFormEntity.class)));

        final String entityContent = EntityUtils.toString(captor.getValue().getEntity());
        assertThat(entityContent, containsString("grant_type=client_credentials"));
        assertThat(entityContent, containsString("client_id=xyz"));
        assertThat(entityContent, containsString("client_secret=abc"));

        verify(mockProvider).getClientCredentialsFor(eq(new URI("https://my.service.to.be.authorised.com/")));
        OAuthTokenAttributes cacheAttributes = testee.getTokenCache().get(clientCredentials);
        assertThat(cacheAttributes.getToken(), sameInstance(token));
    }

    @Test(expected = IOException.class)
    public void testIOExceptionBubbles() throws Exception {
        when(mockProvider.getClientCredentialsFor(Mockito.any(URI.class))).thenReturn(OAuthClientCredentials.newBuilder().clientId("id").clientSecret("Secret").grantType("grant").build());

        final URI uri = new URI("https://my.service.to.be.authorised.com/");

        final HttpClient mockClient = mock(HttpClient.class);
        when(mockPool.getHttpClient()).thenReturn(mockClient);
        when(mockClient.execute(any(HttpPost.class))).thenThrow(IOException.class);

        testee.obtainTokenFor(uri);
    }

    @Test
    public void testHttpConnectionPoolIsCreated() throws Exception {
        final ArgumentCaptor<HttpConnectionConfig> captor = ArgumentCaptor.forClass(HttpConnectionConfig.class);
        verify(mockFactory).getConnectionPool(captor.capture());
        assertThat(captor.getValue(), is(equalTo(HttpConnectionConfig.newBuilder().httpConnectionTimeout(60).httpSocketTimeout(40).maxHttpConnections(20).build())));
    }

    @Test
    public void testIsDependencyInjected() throws Exception {
        assertThat(testee.getHttpConnectionPool(), is(notNullValue()));
        assertThat(testee.getClientCredentialsProvider(), is(notNullValue()));
        assertThat(testee.getObjectMapper(), is(notNullValue()));
    }

    @Test
    public void testIOExceptionWhenClientCredentialsNotFoundException() throws Exception {
        final ClientCredentialsNotFoundException cause = new ClientCredentialsNotFoundException("client credentials not found");
        when(mockProvider.getClientCredentialsFor(any(URI.class))).thenThrow(cause);

        try {
            testee.obtainTokenFor(new URI("https://my.service.to.be.authorised.com/"));
            TestCase.fail("exception expected");
        } catch (IOException e) {
            assertThat(e.getMessage(), is(equalTo("oauth configuration not found for uri")));
            assertThat(e.getCause(), is(sameInstance((Throwable) cause)));
        }
    }

    @Test
    public void testWhenValidTokenAlreadyExistsThenWeReturnTheSameToken() throws Exception {

        OAuthToken fakeCachedToken = OAuthToken.newBuilder().accessToken("whatever").tokenType("good").expiresIn(60).build();

        URI fakeUri = new URI("https://my.service.to.be.authorised.com/");

        OAuthClientCredentials fakeClientCredentials = OAuthClientCredentials.newBuilder().clientId("123").clientSecret("s3cret").authServerURI(new URI("http://my_auth_srv")).grantType("whateveh").build();

        when(mockProvider.getClientCredentialsFor(eq(fakeUri))).thenReturn(fakeClientCredentials);

        when(mockLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        OAuthTokenAttributes fakeOAuthTokenAttributes = OAuthTokenAttributes.newBuilder()
                .token(fakeCachedToken)
                .lock(mockLock)
                .build();
        testee.putToken(fakeClientCredentials, fakeOAuthTokenAttributes);

        OAuthToken receivedToken = (OAuthToken) testee.obtainTokenFor(fakeUri);

        assertThat(receivedToken, is(sameInstance(fakeCachedToken)));

        verifyNoMoreInteractions(mockPool);
    }

    @Test
    public void testWhenURIsRequestingSameTokenAreFoundThenSameTokenIsCached() throws Exception {
        URI fakeUri1 = new URI("http://fakeserver.fakedomain.fake.com");
        URI fakeUri2 = new URI("http://fakeserver.fakedomain.fake.com/");

        final OAuthClientCredentials clientCredentials1 = OAuthClientCredentials.newBuilder().clientId("xyz").clientSecret("abc").grantType("client_credentials").authServerURI(new URI("https://my.oauth.club/")).build();
        final OAuthClientCredentials clientCredentials2 = OAuthClientCredentials.newBuilder().clientId("xyz").clientSecret("abc").grantType("client_credentials").authServerURI(new URI("https://my.oauth.club/")).build();
        when(mockProvider.getClientCredentialsFor(eq(fakeUri1))).thenReturn(clientCredentials1);
        when(mockProvider.getClientCredentialsFor(eq(fakeUri2))).thenReturn(clientCredentials2);

        when(mockLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);

        OAuthToken fakeToken1 = OAuthToken.newBuilder().accessToken("dis_yo_access").tokenType("nice").expiresIn(120).build();
        OAuthTokenAttributes fakeTokenAttributes = OAuthTokenAttributes.newBuilder()
                .token(fakeToken1)
                .lock(mockLock)
                .build();

        testee.putToken(clientCredentials1, fakeTokenAttributes);

        Token receivedToken1 = testee.obtainTokenFor(fakeUri2);

        assertThat(testee.getTokenCache().size(), is(equalTo(1)));
        assertThat(receivedToken1, is(sameInstance((Token) fakeToken1)));

        verifyNoMoreInteractions(mockPool);
    }

    @Test
    public void testWhenTokenIsInMatureStateThenPrefetchIsInvoked() throws Exception {
        final ExecutorService mockExecutorService = mock(ExecutorService.class);

        testee.setExecutorService(mockExecutorService);

        final Future<OAuthToken> mockFuture = mock(Future.class);
        when(mockExecutorService.submit(any(Callable.class))).thenReturn(mockFuture);

        URI fakeUri = new URI("http://fakeserver.fakedomain.fake.com");

        final OAuthClientCredentials clientCredentials = OAuthClientCredentials.newBuilder().clientId("xyz").clientSecret("abc").grantType("client_credentials").authServerURI(new URI("https://my.oauth.club/")).build();

        when(mockProvider.getClientCredentialsFor(eq(fakeUri))).thenReturn(clientCredentials);

        OAuthToken mockToken = Mockito.mock(OAuthToken.class);
        when(mockToken.getRemainingTime()).thenReturn(5L);

        when(mockLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);

        OAuthTokenAttributes fakeOAuthTokenAttributes = OAuthTokenAttributes.newBuilder()
                .token(mockToken)
                .lock(mockLock)
                .build();
        testee.putToken(clientCredentials, fakeOAuthTokenAttributes);

        when(mockLock.tryLock(eq(10L), eq(TimeUnit.SECONDS))).thenReturn(true);

        // test
        testee.obtainTokenFor(fakeUri);

        verify(mockLock).tryLock(eq(10L), eq(TimeUnit.SECONDS));
        verify(mockLock).unlock();
        assertThat(testee.getTokenCache().get(clientCredentials).getJob(), is(notNullValue()));

        ArgumentCaptor<Callable> captor = ArgumentCaptor.forClass(Callable.class);
        verify(mockExecutorService).submit(captor.capture());

        reset();

        // part 2... what happens when we calll this callable???
        final Callable<OAuthToken> actualCallable = captor.getValue();

        final HttpClient mockClient = mock(HttpClient.class);
        when(this.mockPool.getHttpClient()).thenReturn(mockClient);

        final HttpResponse mockResponse = mock(HttpResponse.class);
        when(mockClient.execute(any(HttpPost.class))).thenReturn(mockResponse);
        when(mockResponse.getEntity()).thenReturn(new StringEntity("{\n" +
                "  \"access_token\": \"sparkpost-token\",\n" +
                "  \"token_type\": \"Bearer\",\n" +
                "  \"expires_in\": 60\n" +
                "}"));

        //test..
        final OAuthToken token = actualCallable.call();
        assertThat(token.getValue(), is(equalTo("sparkpost-token")));
        assertThat(token.getTokenType(), is(equalTo("Bearer")));

        // expire 10 seconds less than actual expiry
        assertThat(token.getExpiresIn(), is(equalTo(50L)));
    }

    @Test
    public void testWhenTokenIsNewThenPrefetchIsNotInvoked() throws Exception {
        URI fakeUri = new URI("http://fakeserver.fakedomain.fake.com");

        final OAuthClientCredentials clientCredentials = OAuthClientCredentials.newBuilder().clientId("xyz").clientSecret("abc").grantType("client_credentials").authServerURI(new URI("https://my.oauth.club/")).build();

        when(mockProvider.getClientCredentialsFor(eq(fakeUri))).thenReturn(clientCredentials);

        OAuthToken mockToken = mock(OAuthToken.class);
        when(mockToken.getRemainingTime()).thenReturn(50L);
        OAuthTokenAttributes fakeOAuthTokenAttributes = OAuthTokenAttributes.newBuilder()
                .token(mockToken)
                .lock(mockLock)
                .build();
        testee.putToken(clientCredentials, fakeOAuthTokenAttributes);

        when(mockLock.tryLock(eq(10L), eq(TimeUnit.SECONDS))).thenReturn(true);

        // test
        assertThat(testee.obtainTokenFor(fakeUri), sameInstance((Token) mockToken));

        verify(mockLock).tryLock(eq(10L), eq(TimeUnit.SECONDS));
        verify(mockLock).unlock();
    }

    @Test
    public void testShouldRequestNewTokenIfOldTokenIsExpired() throws Exception {
        OAuthToken fakeToken = OAuthToken.newBuilder()
                .accessToken("dis_yo_access")
                .tokenType("nice")
                .expiresIn(-999)
                .build();

        OAuthClientCredentials clientCredentials = OAuthClientCredentials
                .newBuilder()
                .clientId("xyz")
                .clientSecret("abc")
                .grantType("client_credentials")
                .authServerURI(new URI("https://my.oauth.club/"))
                .build();

        when(mockLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);

        OAuthTokenAttributes fakeOAuthTokenAttributes = OAuthTokenAttributes.newBuilder()
                .token(fakeToken)
                .lock(mockLock)
                .build();

        testee.putToken(clientCredentials, fakeOAuthTokenAttributes);

        URI fakeUri = new URI("http://fakeserver.fakedomain.fake.com");
        when(mockProvider.getClientCredentialsFor(eq(fakeUri))).thenReturn(clientCredentials);

        final HttpClient mockClient = mock(HttpClient.class);
        when(mockPool.getHttpClient()).thenReturn(mockClient);

        final HttpResponse mockHttpResponse = mock(HttpResponse.class);
        when(mockClient.execute(any(HttpPost.class))).thenReturn(mockHttpResponse);

        final StatusLine mockStatusLine = mock(StatusLine.class);
        when(mockHttpResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(200);
        when(mockHttpResponse.getEntity()).thenReturn(new StringEntity("{\n" +
                "  \"access_token\": \"sparkpost-token\",\n" +
                "  \"token_type\": \"Bearer\",\n" +
                "  \"expires_in\": 60\n" +
                "}"));

        final Token token = testee.obtainTokenFor(fakeUri);
        assertThat(token.getValue(), is("sparkpost-token"));

        OAuthTokenAttributes testeeOAuthTokenAttributes = testee.getTokenCache().get(clientCredentials);
        assertThat(testeeOAuthTokenAttributes.getToken(), sameInstance(token));

        assertThat(testee.getTokenCache().size(), is(equalTo(1)));
        verify(mockPool).getHttpClient();
    }

    @Test
    public void testThrowsExceptionWhenGlobalLockThrowsInterruptedException() throws Exception {
        final Lock mockGlobalLock = mock(Lock.class);
        testee.setLock(mockGlobalLock);

        final InterruptedException mockException = mock(InterruptedException.class);
        when(mockGlobalLock.tryLock(eq(10L), eq(TimeUnit.SECONDS))).thenThrow(mockException);

        URI fakeUri = new URI("http://fakeserver.fakedomain.fake.com");
        try {
            testee.obtainTokenFor(fakeUri);
            fail();
        } catch (IOException e) {
            assertThat(e.getMessage(), is(equalTo("error acquiring global lock")));
            assertThat(e.getCause(), sameInstance((Throwable) mockException));
        }
    }

    @Test
    public void testThrowsExceptionWhenCantGetGlobalLockInTime() throws Exception {
        final Lock mockGlobalLock = mock(Lock.class);
        when(mockGlobalLock.tryLock(eq(10L), eq(TimeUnit.SECONDS))).thenReturn(false);
        testee.setLock(mockGlobalLock);

        URI fakeUri = new URI("http://fakeserver.fakedomain.fake.com");

        try {
            testee.obtainTokenFor(fakeUri);
            fail();
        } catch (IOException e) {
            assertThat(e.getMessage(), is(equalTo("failed to acquire global lock in time")));
        }
    }

    @Test
    public void testThrowsExceptionWhenIndividualLockThrowsInterruptedException() throws Exception {
        final InterruptedException mockException = mock(InterruptedException.class);
        when(mockLock.tryLock(eq(10L), eq(TimeUnit.SECONDS))).thenThrow(mockException);

        final OAuthClientCredentials clientCredentials = OAuthClientCredentials
                .newBuilder()
                .clientId("xyz")
                .clientSecret("abc")
                .grantType("client_credentials")
                .authServerURI(new URI("https://my.oauth.club/"))
                .build();

        final OAuthTokenAttributes fakeOAuthTokenAttributes = OAuthTokenAttributes.newBuilder()
                .lock(mockLock)
                .build();

        testee.putToken(clientCredentials, fakeOAuthTokenAttributes);

        URI fakeUri = new URI("http://fakeserver.fakedomain.fake.com");
        when(mockProvider.getClientCredentialsFor(eq(fakeUri))).thenReturn(clientCredentials);

        try {
            testee.obtainTokenFor(fakeUri);
            fail();
        } catch (IOException e) {
            assertThat(e.getMessage(), is(equalTo("error acquiring lock for https://my.oauth.club/")));
            assertThat(e.getCause(), sameInstance((Throwable) mockException));
        }
    }

    @Test
    public void testThrowsExceptionWhenCantGetIndividualLockInTime() throws Exception {
        when(mockLock.tryLock(eq(10L), eq(TimeUnit.SECONDS))).thenReturn(false);

        final OAuthClientCredentials clientCredentials = OAuthClientCredentials
                .newBuilder()
                .clientId("xyz")
                .clientSecret("abc")
                .grantType("client_credentials")
                .authServerURI(new URI("https://my.oauth.club/"))
                .build();

        final OAuthTokenAttributes fakeOAuthTokenAttributes = OAuthTokenAttributes.newBuilder()
                .lock(mockLock)
                .build();

        testee.putToken(clientCredentials, fakeOAuthTokenAttributes);

        URI fakeUri = new URI("http://fakeserver.fakedomain.fake.com");
        when(mockProvider.getClientCredentialsFor(eq(fakeUri))).thenReturn(clientCredentials);

        try {
            testee.obtainTokenFor(fakeUri);
            fail();
        } catch (IOException e) {
            assertThat(e.getCause().getMessage(), is(equalTo("failed to acquire lock in time for https://my.oauth.club/")));
        }
    }

    @Test
    public void testExecutorExceptionWhenGettingPreFetch() throws Exception {
        final Future<OAuthToken> mockJob = mock(Future.class);
        final ExecutionException mockException = mock(ExecutionException.class);
        when(mockJob.get()).thenThrow(mockException);

        final OAuthClientCredentials clientCredentials = OAuthClientCredentials
                .newBuilder()
                .clientId("xyz")
                .clientSecret("abc")
                .grantType("client_credentials")
                .authServerURI(new URI("https://my.oauth.club/"))
                .build();

        when(mockLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);

        final OAuthTokenAttributes fakeOAuthTokenAttributes = OAuthTokenAttributes.newBuilder()
                .lock(mockLock)
                .job(mockJob)
                .build();

        testee.putToken(clientCredentials, fakeOAuthTokenAttributes);

        URI fakeUri = new URI("http://fakeserver.fakedomain.fake.com");
        when(mockProvider.getClientCredentialsFor(eq(fakeUri))).thenReturn(clientCredentials);

        try {
            testee.obtainTokenFor(fakeUri);
            fail();
        } catch (IOException e) {
            assertThat(e.getMessage(), is(equalTo("error requesting oauth token")));
            assertThat(e.getCause(), sameInstance((Throwable) mockException));
        }
    }

    @Test
    public void testShouldUsePrefetchIfThereIsOneInProgress() throws Exception {
        final Future<OAuthToken> mockJob = mock(Future.class);
        final OAuthToken mockNewToken = mock(OAuthToken.class);
        when(mockJob.get()).thenReturn(mockNewToken);
        when(mockNewToken.getRemainingTime()).thenReturn(100L);

        final OAuthToken fakeToken = OAuthToken.newBuilder()
                .expiresIn(-999)
                .build();

        final OAuthClientCredentials clientCredentials = OAuthClientCredentials
                .newBuilder()
                .authServerURI(new URI("https://my.oauth.club/"))
                .build();

        when(mockLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);

        final OAuthTokenAttributes fakeOAuthTokenAttributes = OAuthTokenAttributes.newBuilder()
                .token(fakeToken)
                .lock(mockLock)
                .job(mockJob)
                .build();

        testee.putToken(clientCredentials, fakeOAuthTokenAttributes);

        final URI fakeUri = new URI("http://fakeserver.fakedomain.fake.com");
        when(mockProvider.getClientCredentialsFor(eq(fakeUri))).thenReturn(clientCredentials);

        assertThat(testee.obtainTokenFor(fakeUri), sameInstance((Token) mockNewToken));
        verify(mockJob).get();
    }

    @Test
    public void testShouldNotDoAnotherPrefetchIfThereIsOneInProgress() throws Exception {
        final ExecutorService mockExecutorService = mock(ExecutorService.class);

        testee.setExecutorService(mockExecutorService);

        final Future<OAuthToken> mockFuture = mock(Future.class);
        when(mockExecutorService.submit(any(Callable.class))).thenReturn(mockFuture);

        URI fakeUri = new URI("http://fakeserver.fakedomain.fake.com");

        final OAuthClientCredentials clientCredentials = OAuthClientCredentials.newBuilder().clientId("xyz").clientSecret("abc").grantType("client_credentials").authServerURI(new URI("https://my.oauth.club/")).build();

        when(mockProvider.getClientCredentialsFor(eq(fakeUri))).thenReturn(clientCredentials);

        OAuthToken mockToken = Mockito.mock(OAuthToken.class);
        when(mockToken.getRemainingTime()).thenReturn(5L);

        when(mockLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);

        Future<OAuthToken> mockJob = mock(Future.class);
        OAuthTokenAttributes fakeOAuthTokenAttributes = OAuthTokenAttributes.newBuilder()
                .token(mockToken)
                .job(mockJob)
                .lock(mockLock)
                .build();
        testee.putToken(clientCredentials, fakeOAuthTokenAttributes);

        when(mockLock.tryLock(eq(10L), eq(TimeUnit.SECONDS))).thenReturn(true);

        // test
        testee.obtainTokenFor(fakeUri);
        verifyNoMoreInteractions(mockExecutorService);
    }

    @Test
    public void testShouldDoAnInlineIfThePrefetchTokenHasAlreadyExpired() throws Exception {
        final OAuthToken fakeExistingExpiredToken = OAuthToken.newBuilder()
                .expiresIn(-999)
                .build();

        final OAuthToken fakePrefetchExpiredToken = OAuthToken.newBuilder()
                .expiresIn(-999)
                .build();

        final OAuthClientCredentials clientCredentials = OAuthClientCredentials
                .newBuilder()
                .clientId("xyz")
                .clientSecret("abc")
                .grantType("client_credentials")
                .authServerURI(new URI("https://my.oauth.club/"))
                .build();

        final Future<OAuthToken> mockJob = mock(Future.class);
        when(mockJob.get()).thenReturn(fakePrefetchExpiredToken);

        when(mockLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);

        final OAuthTokenAttributes fakeOAuthTokenAttributes = OAuthTokenAttributes.newBuilder()
                .token(fakeExistingExpiredToken)
                .lock(mockLock)
                .job(mockJob)
                .build();

        // set expired token and expired prefetch
        testee.putToken(clientCredentials, fakeOAuthTokenAttributes);

        final ExecutorService mockExecutorService = mock(ExecutorService.class);
        testee.setExecutorService(mockExecutorService);

        // expect inline prefetch
        final HttpClient mockClient = mock(HttpClient.class);
        when(mockPool.getHttpClient()).thenReturn(mockClient);

        final HttpResponse mockHttpResponse = mock(HttpResponse.class);
        when(mockClient.execute(any(HttpPost.class))).thenReturn(mockHttpResponse);

        final URI fakeUri = new URI("http://fakeserver.fakedomain.fake.com");
        when(mockProvider.getClientCredentialsFor(eq(fakeUri))).thenReturn(clientCredentials);

        final StatusLine mockStatusLine = mock(StatusLine.class);
        when(mockHttpResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(200);
        when(mockHttpResponse.getEntity()).thenReturn(new StringEntity("{\n" +
                "  \"access_token\": \"new token request\",\n" +
                "  \"token_type\": \"Bearer\",\n" +
                "  \"expires_in\": 100\n" +
                "}"));

        final Token token = testee.obtainTokenFor(fakeUri);
        assertThat(token.getValue(), is("new token request"));
    }

    @Test
    public void testObtainTokenThrowsClientSecretException() throws Exception {
        testee.setObjectMapper(new ObjectMapper());

        final HttpClient mockClient = mock(HttpClient.class);
        when(mockPool.getHttpClient()).thenReturn(mockClient);


        final OAuthClientCredentials clientCredentials = OAuthClientCredentials.newBuilder().clientId("xyz").clientSecret("client_credentials").grantType("client_credentials").authServerURI(new URI("https://my.oauth.club/")).build();
        when(mockProvider.getClientCredentialsFor(any(URI.class))).thenReturn(clientCredentials);

        final ClientSecretException clientSecretException = new ClientSecretException("not found");
        when(mockClientSecretService.obtainClientSecret(eq(clientCredentials))).thenThrow(clientSecretException);

        final URI uri = new URI("https://my.service.to.be.authorised.com/");

        try {
            testee.obtainTokenFor(uri);
            TestCase.fail("should not reach here");
        } catch (IOException e) {
            assertThat(e.getMessage(), is(equalTo("error obtaining client secret")));
            assertThat(e.getCause(), sameInstance((Throwable) clientSecretException));
        }

    }
}
