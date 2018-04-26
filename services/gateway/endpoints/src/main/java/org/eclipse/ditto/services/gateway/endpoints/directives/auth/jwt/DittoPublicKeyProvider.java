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
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.services.gateway.security.cache.PublicKeyIdWithIssuer;
import org.eclipse.ditto.services.gateway.security.jwt.ImmutableJsonWebKey;
import org.eclipse.ditto.services.gateway.security.jwt.JsonWebKey;
import org.eclipse.ditto.services.gateway.starter.service.util.HttpClientFacade;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CaffeineCache;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayJwtIssuerNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;

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
    private final Cache<PublicKeyIdWithIssuer, PublicKey> publicKeyCache;

    private DittoPublicKeyProvider(final JwtSubjectIssuersConfig jwtSubjectIssuersConfig,
            final HttpClientFacade httpClient, final int maxCacheEntries, final Duration expiry,
            final Map.Entry<String, MetricRegistry> namedMetricRegistry) {

        this.jwtSubjectIssuersConfig = argumentNotNull(jwtSubjectIssuersConfig);
        this.httpClient = argumentNotNull(httpClient);
        argumentNotNull(expiry);
        argumentNotNull(namedMetricRegistry);

        final AsyncCacheLoader<PublicKeyIdWithIssuer, PublicKey> loader = this::loadPublicKey;

        final Caffeine<PublicKeyIdWithIssuer, PublicKey> caffeine = Caffeine.newBuilder()
                .maximumSize(maxCacheEntries)
                .expireAfterWrite(expiry.getSeconds(), TimeUnit.SECONDS)
                .removalListener(new CacheRemovalListener());
        this.publicKeyCache = CaffeineCache.of(caffeine, loader, namedMetricRegistry);
    }

    /**
     * Returns a new {@code PublicKeyProvider} for the given parameters.
     *
     * @param jwtSubjectIssuersConfig the configuration of supported JWT subject issuers
     * @param httpClient the http client.
     * @param maxCacheEntries the max amount of public keys to cache.
     * @param expiry the expiry of cache entries in minutes.
     * @param namedMetricRegistry the named {@link MetricRegistry} for cache statistics.
     * @return the PublicKeyProvider.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PublicKeyProvider of(final JwtSubjectIssuersConfig jwtSubjectIssuersConfig,
            final HttpClientFacade httpClient,
            final int maxCacheEntries, final Duration expiry,
            final Map.Entry<String, MetricRegistry> namedMetricRegistry) {
        return new DittoPublicKeyProvider(jwtSubjectIssuersConfig, httpClient, maxCacheEntries, expiry,
                namedMetricRegistry);
    }

    @Override
    public CompletableFuture<Optional<PublicKey>> getPublicKey(final String issuer, final String keyId) {
        argumentNotNull(issuer);
        argumentNotNull(keyId);

        return publicKeyCache.get(PublicKeyIdWithIssuer.of(keyId, issuer));
    }

    /* this method is used to asynchronously load the public key into the cache */
    private CompletableFuture<PublicKey> loadPublicKey(final PublicKeyIdWithIssuer publicKeyIdWithIssuer,
            final Executor executor) {
        final String issuer = publicKeyIdWithIssuer.getIssuer();
        final String keyId = publicKeyIdWithIssuer.getKeyId();
        LOGGER.debug("Loading public key with id <{}> from issuer <{}>.", keyId, issuer);

        final JwtSubjectIssuerConfig subjectIssuerConfig =
                jwtSubjectIssuersConfig.getConfigItem(issuer)
                        .orElseThrow(() -> GatewayJwtIssuerNotSupportedException.newBuilder(issuer).build());

        final String jwkResource = subjectIssuerConfig.getJwkResource();
        final CompletableFuture<HttpResponse> responseFuture =
                CompletableFuture.supplyAsync(() -> getPublicKeysFromJwkResource(jwkResource));
        final CompletableFuture<JsonArray> publicKeysFuture =
                responseFuture.thenCompose(this::mapResponseToJsonArray);
        return publicKeysFuture.thenApply(publicKeysArray -> mapToPublicKey(publicKeysArray, keyId, jwkResource))
                .toCompletableFuture();
    }

    private CompletableFuture<JsonArray> mapResponseToJsonArray(final HttpResponse response) {
        final CompletionStage<JsonObject> body =
                response.entity().getDataBytes().fold(ByteString.empty(), ByteString::concat)
                        .map(ByteString::utf8String)
                        .map(JsonFactory::readFrom)
                        .map(JsonValue::asObject)
                        .runWith(Sink.head(), httpClient.getActorMaterializer());

        final JsonPointer keysPointer = JsonPointer.of("keys");

        return body.toCompletableFuture()
                .thenApply(jsonObject -> jsonObject.getValue(keysPointer).map(JsonValue::asArray)
                        .orElseThrow(() -> new JsonMissingFieldException(keysPointer)))
                .exceptionally(t -> {
                    throw new IllegalStateException("Failed to extract public keys from JSON response: " + body, t);
                });
    }

    private HttpResponse getPublicKeysFromJwkResource(final String resource) {
        LOGGER.debug("Loading public keys from resource <{}>.", resource);

        final HttpResponse response;
        try {
            response = httpClient.createSingleHttpRequest(HttpRequest.GET(resource)).toCompletableFuture()
                    .get(JWK_REQUEST_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
        } catch (final ExecutionException | InterruptedException | TimeoutException e) {
            throw new IllegalStateException(MessageFormat.format("Got Exception from JwkResource provider at " +
                    "resource <{0}>.", resource), e);
        }
        return response;
    }

    private PublicKey mapToPublicKey(final JsonArray publicKeys, final String keyId, final String resource) {
        LOGGER.debug("Trying to find key with id <{}> in json array <{}>.", keyId, publicKeys);

        for (final JsonValue jsonValue : publicKeys) {
            try {
                final JsonObject jsonObject = jsonValue.asObject();
                final JsonWebKey jsonWebKey = ImmutableJsonWebKey.fromJson(jsonObject);

                if (jsonWebKey.getId().equals(keyId)) {
                    LOGGER.debug("Found matching JsonWebKey for id <{}>: <{}>.", keyId, jsonWebKey);
                    final KeyFactory keyFactory = KeyFactory.getInstance(jsonWebKey.getType());
                    final RSAPublicKeySpec rsaPublicKeySpec =
                            new RSAPublicKeySpec(jsonWebKey.getModulus(), jsonWebKey.getExponent());
                    return keyFactory.generatePublic(rsaPublicKeySpec);
                }
            } catch (final NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new IllegalStateException(MessageFormat.format("Got invalid key from JwkResource provider " +
                        "at resource <{0}>.", resource), e);
            }
        }

        LOGGER.debug("Did not find key with id <{}>.", keyId);
        return null;
    }

    private static final class CacheRemovalListener implements RemovalListener<PublicKeyIdWithIssuer, PublicKey> {

        @Override
        public void onRemoval(@Nullable final PublicKeyIdWithIssuer key, @Nullable final PublicKey value,
                @Nonnull final com.github.benmanes.caffeine.cache.RemovalCause cause) {
            final String msgTemplate = "Removed PublicKey with ID <{}> from cache due to cause '{}'.";
            LOGGER.debug(msgTemplate, key, cause);
        }
    }
}
