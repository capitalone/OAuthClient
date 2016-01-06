package com.capitalone.auth.oauth.framework;

import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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
public class OAuthClientCredentialsProviderTest {

    private OAuthClientCredentialsProvider testee;
    private OAuthClientCredentials fakeClientCredentials;

    @Before
    public void setup() throws URISyntaxException {
        fakeClientCredentials = OAuthClientCredentials.newBuilder()
                .clientId("clientId")
                .clientSecret("clientSecret")
                .authServerURI(new URI("https://my.oauth.club/"))
                .grantType("grantType")
                .clientURIRegex("^http[s]{0,1}://my.service.to.be.authorised.com[/]{0,1}$")
                .clientSecretEncryptionKey("clientSecretEncryptionKey")
                .build();

        OAuthClientCredentialsProvider oAuthClientCredentialsProvider = new OAuthClientCredentialsProvider(new OAuthClientCredentials[]{fakeClientCredentials});
        testee = oAuthClientCredentialsProvider;
    }

    @Test
    public void testGetClientCredentialsFor() throws Exception {
        final OAuthClientCredentials clientCredentials = testee.getClientCredentialsFor(new URI("https://my.service.to.be.authorised.com"));
        assertThat(clientCredentials.getGrantType(), is(equalTo("grantType")));
        assertThat(clientCredentials.getClientId(), is(equalTo("clientId")));
        assertThat(clientCredentials.getClientSecret(), is(equalTo("clientSecret")));
        assertThat(clientCredentials.getClientSecretEncryptionKey(), is(equalTo("clientSecretEncryptionKey")));
        assertThat(clientCredentials.getAuthServerURI(), is(equalTo(new URI("https://my.oauth.club/"))));
    }

    @Test
    public void testGetClientCredentialsFor_clientURIDoesNotMatch() throws Exception {
        try {
            testee.getClientCredentialsFor(new URI("https://not.my.service.to.be.authorised.com"));
            TestCase.fail("exception expected");
        } catch (ClientCredentialsNotFoundException e) {
            assertThat(e.getMessage(), is(equalTo("client credentials not found")));
        }
    }

    @Test
    public void testWhenComparedWithDifferentInstanceWithSameValuesThenItReturnsTrue() throws Exception {
        final URI uri = new URI("https://my.service.to.be.authorised.com");
        final OAuthClientCredentials clientCredentials1 = testee.getClientCredentialsFor(uri);
        final OAuthClientCredentials clientCredentials2 = testee.getClientCredentialsFor(uri);

        assertThat(clientCredentials1, is(sameInstance(clientCredentials2)));
        assertThat(clientCredentials1, is(equalTo(clientCredentials2)));
        assertThat(clientCredentials2, is(equalTo(clientCredentials1)));
    }
}