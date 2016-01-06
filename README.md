# Java Auth Client Library

# Table of Contents

1. [Links](#links)
2. [Overview](#overview)
  1. [Obtaining OAuth Token](#obtaining-the-oauth-token)
  2. [Maintaining OAuth Token Lifecycle](#maintaining-oauth-token-lifecycle)
3. [Example Usage](#example-usage)
4. [Contributors](#contributors)
5. [Copyright](#copyright)

## Overview
Supports OAuth authentication and provides interfaces to add more authentication methods. 

Conceptually, the library has two parts:
1. Obtaining the OAuth token
2. Maintaining OAuth token lifecycle

### Obtaining the OAuth token
`OAuthClientCredentials` is at the base of obtaining a token. It holds the information about the client requesting the app. This is:
1. `grantType` (Type of grant that is being requested)
2. `clientId` (ID of your app)
3. `clientSecret` (Secret of your app)

This class also holds other bits of information like the URL of the authorisation server (`authURI`) and the regular expression pattern (`clientURIRegex`) to match the client URIs needing the authorisation.

Suppose you have two URIs - one http://awesomeserver.com/hello and http://coolserver.com/hello being the another. If both of these require authorisation from the same server and if that authorisation can be fulfilled with the same set of client credentials, then you only need one instance of `OAuthClientCredentials` class but with a regular expression in clientURIRegex that can match both of those URIs. Howeever, if both of those URIs require different set of client credentials or a different authorisation server or both, then you need two separate instances.

Once you have one or more instances of `OAuthClientCredentials` class, create an instance of `ClientCredentialsProvider` class with type `OAuthClientCredentials`. This class needs at least one instance of the `OAuthClientCredentials` and thus requires it to be passed in the constructor.

The `ClientCredentialsProvider`, as the name suggests provides the credentials upon request. When `getClientCredentialsFor` is invoked with a URI, it loops through all of its `OAuthClientCredentials` objects and runs the `clientURIRegex` match against the given URI. It returns the first `OAuthClientCredentials` that matches the URI.

Once you have a working instance of `ClientCredentialsProvider`, create an instance of OAuthTokenService. The constructor needs the following:
1. `httpConnectionFactory` (Factory generating your HTTP connections)
2. `httpConnectionConfig` (Configuration for your HTTP connections. This includes sslProtocol, which defaults to TLSv1.2)
3. `prefetchPoolSize` (Size of your prefetch pool - more on this later)
4. `oAuthClientCredentialsProvider` (Your OAuthClientCredentials provider instance)

When requesting the token for the first time (using `obtainTokenFor`), the service simply returns the `OAuthToken` object as `Token`.

### Maintaining OAuth token lifecycle
The `Token` object is cached as soon as it is returned for the first time. When a request comes for the next time and if the token has not expired, it simply returns the cached token. 

However, if the token has certain amount of time left (defined in `prefetchTimeout`) before expiry, the token service fires off a prefetch job which asynchronously updates the token. All requests coming in during this time period use the token that has been cached and is about to expire. Once the asynchronous job returns a valid token, the current token is replaced with that token which is then returned to all subsequent requests.

If the requests have slowed down and the `OAuthTokenService` didn't get a chance to update the token asynchronously, it simply blocks the current request thread and gets the token synchronously (which it then caches).

## Example Usage
Include the following in your gradle file. Make sure you replace $version what whatever version of the library you want to use.
```groovy
dependencies {
    compile 'com.capitalone.util:c1-oauth-client-java:$version'
}
```

Here's an example client application using the library in action:
```java
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

public class ExampleClient {

    private final TokenService oAuthTokenService;

    public static void main(String[] args) {
        ExampleClient client = new ExampleClient();
        client.makeRequest();
    }

    public ExampleClient() {
        URI authServerURI = null;

        try {
            authServerURI = new URI("https://myoauthserver.com/oauth/oauth20/token");
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
                .sslProtocol("TLSv1.2")
                .build();

        oAuthTokenService = new OAuthTokenService(httpConnectionFactory, httpConnectionConfig, 20, 10, clientCredentialsProvider, clientSecretService);
    }

    public void makeRequest() {
        try {
            OAuthToken token = (OAuthToken) oAuthTokenService.obtainTokenFor(new URI("https://myoauthserver.com/partners/sparkpost/transmissions"));
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
```

## Dependencies
| Library                                        | Version | License              |
| ---------------------------------------------- | ------- | -------------------- |
| commons-io:commons-io                          |     2.4 | Apache 2.0           |
| org.apache.httpcomponents:httpclient           |   4.5.2 | Apache 2.0           |
| com.fasterxml.jackson.core:jackson-databind    |   2.3.4 | Apache 2.0           |
| commons-lang:commons-lang                      |     2.6 | Apache 2.0           |
| junit:junit                                    |    4.11 | CAPL 1.0, CPL 1.0    |
| org.mockito:mockito-core                       | 1.10.19 | MIT                  |
| org.hamcrest:hamcrest-all                      |     1.3 | BSD 2 -clause        |


## Contributors
This project adheres to the [Open Source Code of Conduct](http://www.capitalone.io/codeofconduct). By participating, you are expected to honor this code.

## Copyright
Copyright (c) 2016 Capital One Services, LLC

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUTNOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.