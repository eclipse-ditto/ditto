/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.gateway.security.authentication.jwt;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotNull;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.text.MessageFormat;
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
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.services.gateway.security.cache.PublicKeyIdWithIssuer;
import org.eclipse.ditto.services.gateway.util.HttpClientFacade;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CaffeineCache;
import org.eclipse.ditto.services.utils.cache.config.CacheConfig;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayJwtIssuerNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final String OPENID_CONNECT_DISCOVERY_PATH = "/.well-known/openid-configuration";
    private static final String HTTPS = "https://";
    private static final JsonFieldDefinition<String> JSON_JWKS_URI = JsonFieldDefinition.ofString("jwks_uri");

    private final JwtSubjectIssuersConfig jwtSubjectIssuersConfig;
    private final HttpClientFacade httpClient;
    private final Cache<PublicKeyIdWithIssuer, PublicKey> publicKeyCache;

    private DittoPublicKeyProvider(final JwtSubjectIssuersConfig jwtSubjectIssuersConfig,
            final HttpClientFacade httpClient,
            final CacheConfig publicKeysConfig,
            final String cacheName) {

        this.jwtSubjectIssuersConfig = argumentNotNull(jwtSubjectIssuersConfig);
        this.httpClient = argumentNotNull(httpClient);
        argumentNotNull(publicKeysConfig, "config of the public keys cache");
        argumentNotNull(cacheName);

        final AsyncCacheLoader<PublicKeyIdWithIssuer, PublicKey> loader = this::loadPublicKey;

        final Caffeine<PublicKeyIdWithIssuer, PublicKey> caffeine = Caffeine.newBuilder()
                .maximumSize(publicKeysConfig.getMaximumSize())
                .expireAfterWrite(publicKeysConfig.getExpireAfterWrite())
                .removalListener(new CacheRemovalListener());

        publicKeyCache = CaffeineCache.of(caffeine, loader, cacheName);
    }

    /**
     * Returns a new {@code PublicKeyProvider} for the given parameters.
     *
     * @param jwtSubjectIssuersConfig the configuration of supported JWT subject issuers
     * @param httpClient the http client.
     * @param publicKeysCacheConfig the config of the public keys cache.
     * @param cacheName The name of the cache.
     * @return the PublicKeyProvider.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PublicKeyProvider of(final JwtSubjectIssuersConfig jwtSubjectIssuersConfig,
            final HttpClientFacade httpClient,
            final CacheConfig publicKeysCacheConfig,
            final String cacheName) {

        return new DittoPublicKeyProvider(jwtSubjectIssuersConfig, httpClient, publicKeysCacheConfig, cacheName);
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

        final String discoveryEndpoint = getDiscoveryEndpoint(subjectIssuerConfig.getIssuer());
        final CompletableFuture<HttpResponse> responseFuture =
                CompletableFuture.supplyAsync(() -> getPublicKeysFromDiscoveryEndpoint(discoveryEndpoint));
        final CompletableFuture<JsonArray> publicKeysFuture =
                responseFuture.thenCompose(this::mapResponseToJsonArray);
        return publicKeysFuture.thenApply(publicKeysArray -> mapToPublicKey(publicKeysArray, keyId, discoveryEndpoint))
                .toCompletableFuture();
    }

    private String getDiscoveryEndpoint(final String issuer) {
        final String iss;
        if (issuer.endsWith("/")) {
            iss = issuer.substring(0, issuer.length() - 1);
        } else {
            iss = issuer;
        }
        return HTTPS + iss + OPENID_CONNECT_DISCOVERY_PATH;
    }

    private CompletableFuture<JsonArray> mapResponseToJsonArray(final HttpResponse response) {
        final CompletionStage<JsonObject> body = mapResponseToJsonObject(response);

        final JsonPointer keysPointer = JsonPointer.of("keys");

        return body.toCompletableFuture()
                .thenApply(jsonObject -> jsonObject.getValue(keysPointer).map(JsonValue::asArray)
                        .orElseThrow(() -> new JsonMissingFieldException(keysPointer)))
                .exceptionally(t -> {
                    throw new IllegalStateException("Failed to extract public keys from JSON response: " + body, t);
                });
    }

    private HttpResponse getPublicKeysFromDiscoveryEndpoint(final String discoveryEndpoint) {
        LOGGER.debug("Loading public keys from discovery endpoint <{}>.", discoveryEndpoint);

        final HttpResponse response;
        try {
            response = httpClient.createSingleHttpRequest(HttpRequest.GET(discoveryEndpoint))
                    .thenCompose(this::mapResponseToJsonObject)
                    .thenApply(jsonObject -> jsonObject.getValueOrThrow(JSON_JWKS_URI))
                    .thenCompose(jwksUri -> httpClient.createSingleHttpRequest(HttpRequest.GET(jwksUri)))
                    .toCompletableFuture()
                    .get(JWK_REQUEST_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
        } catch (final ExecutionException | InterruptedException | TimeoutException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    MessageFormat.format("Got Exception from discovery endpoint <{0}>.", discoveryEndpoint), e);
        }
        return response;
    }

    private CompletionStage<JsonObject> mapResponseToJsonObject(final HttpResponse response) {
        return response.entity().getDataBytes().fold(ByteString.empty(), ByteString::concat)
                .map(ByteString::utf8String)
                .map(JsonFactory::readFrom)
                .map(JsonValue::asObject)
                .runWith(Sink.head(), httpClient.getActorMaterializer());
    }

    private static PublicKey mapToPublicKey(final JsonArray publicKeys, final String keyId,
            final String discoveryEndpoint) {
        LOGGER.debug("Trying to find key with id <{}> in json array <{}>.", keyId, publicKeys);

        for (final JsonValue jsonValue : publicKeys) {
            try {
                final JsonObject jsonObject = jsonValue.asObject();
                final JsonWebKey jsonWebKey = ImmutableJsonWebKey.fromJson(jsonObject);

                if (jsonWebKey.getId().equals(keyId)) {
                    LOGGER.debug("Found matching JsonWebKey for id <{}>: <{}>.", keyId, jsonWebKey);
                    final KeyFactory keyFactory = KeyFactory.getInstance(jsonWebKey.getType());
                    final KeySpec rsaPublicKeySpec =
                            new RSAPublicKeySpec(jsonWebKey.getModulus(), jsonWebKey.getExponent());
                    return keyFactory.generatePublic(rsaPublicKeySpec);
                }
            } catch (final NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new IllegalStateException(MessageFormat.format("Got invalid key from JwkResource provider " +
                        "at discovery endpoint <{0}>.", discoveryEndpoint), e);
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
