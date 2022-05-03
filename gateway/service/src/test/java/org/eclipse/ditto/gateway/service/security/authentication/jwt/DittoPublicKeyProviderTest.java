/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.gateway.service.security.authentication.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.ditto.gateway.api.GatewayAuthenticationProviderUnavailableException;
import org.eclipse.ditto.gateway.service.util.config.security.OAuthConfig;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.http.HttpClientFacade;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.jwt.model.JsonWebKey;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;

@RunWith(MockitoJUnitRunner.class)
public final class DittoPublicKeyProviderTest {

    private static final Long LATCH_TIMEOUT = 20L;

    private static final HttpRequest DISCOVERY_ENDPOINT_REQUEST =
            HttpRequest.GET("https://google.com/.well-known/openid-configuration");
    private static final String JWKS_URI = "https://the.uri.com";
    private static final HttpRequest PUBLIC_KEYS_REQUEST = HttpRequest.GET(JWKS_URI);
    private static final String KEY_ID = "cc34c0a0-bd5a-4a3c-a50d-a2a7db7643df";

    private ActorSystem actorSystem;
    private PublicKeyProvider underTest;

    @Mock
    public HttpClientFacade httpClientMock;

    @Mock
    public CacheConfig cacheConfigMock;

    @Mock
    public OAuthConfig oauthConfigMock;

    @Before
    public void setup() {
        actorSystem = ActorSystem.create(getClass().getSimpleName());
        when(httpClientMock.getActorSystem()).thenReturn(actorSystem);
        final JwtSubjectIssuersConfig subjectIssuersConfig = JwtSubjectIssuersConfig.fromJwtSubjectIssuerConfigs(
                Collections.singleton(new JwtSubjectIssuerConfig(SubjectIssuer.GOOGLE, "google.com")));
        when(cacheConfigMock.getMaximumSize()).thenReturn(100L);
        when(cacheConfigMock.getExpireAfterWrite()).thenReturn(Duration.ofMinutes(3));
        when(oauthConfigMock.getAllowedClockSkew()).thenReturn(Duration.ofSeconds(1));
        underTest = DittoPublicKeyProvider.of(subjectIssuersConfig, httpClientMock, cacheConfigMock,
                getClass().getSimpleName(), oauthConfigMock);
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            actorSystem.terminate();
            actorSystem = null;
        }
    }

    @Test
    public void verifyThatKeyIsCached() throws InterruptedException, TimeoutException, ExecutionException {

        mockSuccessfulDiscoveryEndpointRequest();
        mockSuccessfulPublicKeysRequest();

        final Optional<PublicKeyWithParser> publicKeyFromEndpoint =
                underTest.getPublicKeyWithParser("google.com", KEY_ID).get(LATCH_TIMEOUT, TimeUnit.SECONDS);
        assertThat(publicKeyFromEndpoint).isNotEmpty();
        verify(httpClientMock).createSingleHttpRequest(DISCOVERY_ENDPOINT_REQUEST);
        verify(httpClientMock).createSingleHttpRequest(PUBLIC_KEYS_REQUEST);

        Mockito.clearInvocations(httpClientMock);

        final Optional<PublicKeyWithParser> publicKeyFromCache =
                underTest.getPublicKeyWithParser("google.com", KEY_ID).get(LATCH_TIMEOUT, TimeUnit.SECONDS);
        assertThat(publicKeyFromCache).contains(publicKeyFromEndpoint.get());
        assertThat(publicKeyFromCache).isNotEmpty();
        verifyNoMoreInteractions(httpClientMock);
    }

    @Test
    public void verifyThatKeyIsNotCachedOnErrorResponseFromDiscoveryEndpoint() {

        mockErrorDiscoveryEndpointRequest();

        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(() -> underTest.getPublicKeyWithParser("google.com", KEY_ID).get(LATCH_TIMEOUT, TimeUnit.SECONDS))
                .withCauseExactlyInstanceOf(GatewayAuthenticationProviderUnavailableException.class);
        verify(httpClientMock).createSingleHttpRequest(DISCOVERY_ENDPOINT_REQUEST);
        verify(httpClientMock, never()).createSingleHttpRequest(PUBLIC_KEYS_REQUEST);

        Mockito.clearInvocations(httpClientMock);

        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(() -> underTest.getPublicKeyWithParser("google.com", KEY_ID).get(LATCH_TIMEOUT, TimeUnit.SECONDS))
                .withCauseExactlyInstanceOf(GatewayAuthenticationProviderUnavailableException.class);
        verify(httpClientMock).createSingleHttpRequest(DISCOVERY_ENDPOINT_REQUEST);
        verify(httpClientMock, never()).createSingleHttpRequest(PUBLIC_KEYS_REQUEST);
    }

    @Test
    public void verifyThatKeyIsNotCachedIfResponseDoesNotContainKeyId()
            throws InterruptedException, ExecutionException, TimeoutException {

        mockSuccessfulDiscoveryEndpointRequest();
        mockSuccessfulPublicKeysRequestWithoutMatchingKeyId();

        assertThat(underTest.getPublicKeyWithParser("google.com", KEY_ID).get(LATCH_TIMEOUT, TimeUnit.SECONDS)).isEmpty();
        verify(httpClientMock).createSingleHttpRequest(DISCOVERY_ENDPOINT_REQUEST);
        verify(httpClientMock).createSingleHttpRequest(PUBLIC_KEYS_REQUEST);

        Mockito.clearInvocations(httpClientMock);

        assertThat(underTest.getPublicKeyWithParser("google.com", KEY_ID).get(LATCH_TIMEOUT, TimeUnit.SECONDS)).isEmpty();
        verify(httpClientMock).createSingleHttpRequest(DISCOVERY_ENDPOINT_REQUEST);
        verify(httpClientMock).createSingleHttpRequest(PUBLIC_KEYS_REQUEST);
    }

    private void mockSuccessfulDiscoveryEndpointRequest() {
        final JsonObject jwksUriResponse = JsonObject.newBuilder().set("jwks_uri", JWKS_URI).build();
        final HttpResponse discoveryEndpointResponse = HttpResponse.create().withStatus(StatusCodes.OK)
                .withEntity(jwksUriResponse.toString());
        when(httpClientMock.createSingleHttpRequest(DISCOVERY_ENDPOINT_REQUEST))
                .thenReturn(CompletableFuture.completedFuture(discoveryEndpointResponse));
    }

    private void mockErrorDiscoveryEndpointRequest() {
        final JsonObject errorBody = JsonObject.newBuilder().set("message", "Something went wrong.").build();
        final HttpResponse discoveryEndpointResponse =
                HttpResponse.create().withStatus(StatusCodes.SERVICE_UNAVAILABLE).withEntity(errorBody.toString());
        when(httpClientMock.createSingleHttpRequest(DISCOVERY_ENDPOINT_REQUEST))
                .thenReturn(CompletableFuture.completedFuture(discoveryEndpointResponse));
    }

    private void mockSuccessfulPublicKeysRequest() {

        final JsonObject jsonWebKey = JsonObject.newBuilder()
                .set(JsonWebKey.JsonFields.KEY_TYPE, "RSA")
                .set(JsonWebKey.JsonFields.KEY_ALGORITHM, "HS256")
                .set(JsonWebKey.JsonFields.KEY_USAGE, "sig")
                .set(JsonWebKey.JsonFields.KEY_ID, KEY_ID)
                .set(JsonWebKey.JsonFields.KEY_MODULUS,
                        "pjdss8ZaDfEH6K6U7GeW2nxDqR4IP049fk1fK0lndimbMMVBdPv_hSpm8T8EtBDxrUdi1OHZfMhUixGaut-3nQ4GG9nM249oxhCtxqqNvEXrmQRGqczyLxuh-fKn9Fg--hS9UpazHpfVAFnB5aCfXoNhPuI8oByyFKMKaOVgHNqP5NBEqabiLftZD3W_lsFCPGuzr4Vp0YS7zS2hDYScC2oOMu4rGU1LcMZf39p3153Cq7bS2Xh6Y-vw5pwzFYZdjQxDn8x8BG3fJ6j8TGLXQsbKH1218_HcUJRvMwdpbUQG5nvA2GXVqLqdwp054Lzk9_B_f1lVrmOKuHjTNHq48w")
                .set(JsonWebKey.JsonFields.KEY_EXPONENT, "AQAB")
                .build();
        final JsonArray keysArray = JsonArray.newBuilder().add(jsonWebKey).build();
        final JsonObject keysJson = JsonObject.newBuilder().set("keys", keysArray).build();
        final HttpResponse publicKeysResponse = HttpResponse.create()
                .withStatus(StatusCodes.OK)
                .withEntity(keysJson.toString());
        when(httpClientMock.createSingleHttpRequest(PUBLIC_KEYS_REQUEST))
                .thenReturn(CompletableFuture.completedFuture(publicKeysResponse));
    }

    private void mockSuccessfulPublicKeysRequestWithoutMatchingKeyId() {

        final JsonObject jsonWebKey = JsonObject.newBuilder()
                .set(JsonWebKey.JsonFields.KEY_TYPE, "RSA")
                .set(JsonWebKey.JsonFields.KEY_ALGORITHM, "HS256")
                .set(JsonWebKey.JsonFields.KEY_USAGE, "sig")
                .set(JsonWebKey.JsonFields.KEY_ID, "anotherId")
                .set(JsonWebKey.JsonFields.KEY_MODULUS,
                        "pjdss8ZaDfEH6K6U7GeW2nxDqR4IP049fk1fK0lndimbMMVBdPv_hSpm8T8EtBDxrUdi1OHZfMhUixGaut-3nQ4GG9nM249oxhCtxqqNvEXrmQRGqczyLxuh-fKn9Fg--hS9UpazHpfVAFnB5aCfXoNhPuI8oByyFKMKaOVgHNqP5NBEqabiLftZD3W_lsFCPGuzr4Vp0YS7zS2hDYScC2oOMu4rGU1LcMZf39p3153Cq7bS2Xh6Y-vw5pwzFYZdjQxDn8x8BG3fJ6j8TGLXQsbKH1218_HcUJRvMwdpbUQG5nvA2GXVqLqdwp054Lzk9_B_f1lVrmOKuHjTNHq48w")
                .set(JsonWebKey.JsonFields.KEY_EXPONENT, "AQAB")
                .build();
        final JsonArray keysArray = JsonArray.newBuilder().add(jsonWebKey).build();
        final JsonObject keysJson = JsonObject.newBuilder().set("keys", keysArray).build();
        final HttpResponse publicKeysResponse = HttpResponse.create()
                .withStatus(StatusCodes.OK)
                .withEntity(keysJson.toString());
        when(httpClientMock.createSingleHttpRequest(PUBLIC_KEYS_REQUEST))
                .thenReturn(CompletableFuture.completedFuture(publicKeysResponse));
    }

}
