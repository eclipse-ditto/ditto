/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotNull;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.services.gateway.security.cache.Cache;
import org.eclipse.ditto.services.gateway.security.cache.PublicKeyCache;
import org.eclipse.ditto.services.gateway.security.jwt.ImmutableJsonWebKey;
import org.eclipse.ditto.services.gateway.security.jwt.JsonWebKey;
import org.eclipse.ditto.services.gateway.starter.service.util.HttpClientFacade;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationProviderUnavailableException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayJwtIssuerNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.javadsl.Sink;
import akka.util.ByteString;


/**
 * Implementation of {@link PublicKeyProvider}. This provider requests keys at the {@link SubjectIssuer} and caches
 * responses to reduce network io.
 */
public final class DittoPublicKeyProvider implements PublicKeyProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(DittoPublicKeyProvider.class);

    private static final long JWK_REQUEST_TIMEOUT_MILLISECONDS = 5000;

    private final JwtSubjectIssuersConfig jwtSubjectIssuersConfig;
    private final HttpClientFacade httpClient;
    private final Cache<String, PublicKey> publicKeyCache;

    private DittoPublicKeyProvider(final JwtSubjectIssuersConfig jwtSubjectIssuersConfig,
            final HttpClientFacade httpClient, final Cache<String, PublicKey> publicKeyCache) {
        this.jwtSubjectIssuersConfig = argumentNotNull(jwtSubjectIssuersConfig);
        this.httpClient = argumentNotNull(httpClient);
        this.publicKeyCache = argumentNotNull(publicKeyCache);
    }


    /**
     * Returns a new {@code PublicKeyProvider} for the given parameters.
     *
     * @param jwtSubjectIssuersConfig the configuration of supported JWT subject issuers
     * @param httpClient the http client.
     * @param maxCacheEntries the max amount of public keys to cache.
     * @param expiry the expiry of cache entries in minutes.
     * @return the PublicKeyProvider.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PublicKeyProvider of(final JwtSubjectIssuersConfig jwtSubjectIssuersConfig,
            final HttpClientFacade httpClient,
            final int maxCacheEntries, final Duration expiry) {
        return new DittoPublicKeyProvider(jwtSubjectIssuersConfig, httpClient,
                PublicKeyCache.newInstance(maxCacheEntries, expiry));
    }

    @Override
    public Optional<PublicKey> getPublicKey(final String issuer, final String keyId) {
        argumentNotNull(issuer);
        argumentNotNull(keyId);

        final JwtSubjectIssuerConfig subjectIssuerConfig =
                jwtSubjectIssuersConfig.getConfigItem(issuer)
                        .orElseThrow(() -> GatewayJwtIssuerNotSupportedException.newBuilder(issuer).build());

        return Optional.ofNullable(publicKeyCache.get(keyId).orElseGet(() -> {
            try {
                final JsonArray publicKeys = getPublicKeysFromJwkResource(subjectIssuerConfig.getJwkResource());
                return refreshCache(publicKeys, keyId).orElse(null);
            } catch (final RuntimeException e) {
                LOGGER.warn("An error occurred while retrieving a JWK: ", e);
                return null;
            }
        }));
    }

    private JsonArray getPublicKeysFromJwkResource(final String resource) {
        final HttpResponse response;
        try {
            response = httpClient.createSingleHttpRequest(HttpRequest.GET(resource)).toCompletableFuture()
                    .get(JWK_REQUEST_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
        } catch (final ExecutionException | InterruptedException | TimeoutException e) {
            LOGGER.warn("Got Exception from JwkResource provider at resource '{}': {} - {}", resource,
                    e.getClass().getSimpleName(), e.getMessage());
            throw GatewayAuthenticationProviderUnavailableException.newBuilder().cause(e).build();
        }

        try {
            final CompletionStage<JsonObject> body =
                    response.entity().getDataBytes().fold(ByteString.empty(), ByteString::concat)
                            .map(ByteString::utf8String)
                            .map(JsonFactory::readFrom)
                            .map(JsonValue::asObject)
                            .runWith(Sink.head(), httpClient.getActorMaterializer());

            final JsonPointer keysPointer = JsonPointer.of("keys");

            return body.toCompletableFuture().get().getValue(keysPointer).map(JsonValue::asArray)
                    .orElseThrow(() -> new JsonMissingFieldException(keysPointer));
        } catch (final InterruptedException | ExecutionException e) {
            LOGGER.warn("Could not parse JSON. Was: {}", response);
            throw new IllegalStateException("Failed to extract public keys from JSON!", e);
        }
    }

    private Optional<PublicKey> refreshCache(final JsonArray publicKeys, final String keyId) {
        PublicKey key = null;

        for (final JsonValue jsonValue : publicKeys) {
            final PublicKey publicKey;
            try {
                final JsonWebKey keyFromJson = ImmutableJsonWebKey.fromJson(jsonValue.asObject());
                final KeyFactory keyFactory = KeyFactory.getInstance(keyFromJson.getType());
                final RSAPublicKeySpec rsaPublicKeySpec =
                        new RSAPublicKeySpec(keyFromJson.getModulus(), keyFromJson.getExponent());
                publicKey = keyFactory.generatePublic(rsaPublicKeySpec);

                if (keyFromJson.getId().equals(keyId)) {
                    key = publicKey;
                }

                publicKeyCache.put(keyFromJson.getId(), publicKey);
            } catch (final NoSuchAlgorithmException | InvalidKeySpecException e) {
                LOGGER.warn("Got invalid key from authentication provider: '{}'", e);
            }
        }

        return Optional.ofNullable(key);
    }

}
