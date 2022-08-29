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
package org.eclipse.ditto.gateway.service.security.authentication.jwt;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotNull;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.gateway.api.GatewayAuthenticationProviderUnavailableException;
import org.eclipse.ditto.gateway.api.GatewayJwtIssuerNotSupportedException;
import org.eclipse.ditto.gateway.service.security.cache.PublicKeyIdWithIssuer;
import org.eclipse.ditto.gateway.service.util.config.security.OAuthConfig;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CaffeineCache;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.http.HttpClientFacade;
import org.eclipse.ditto.internal.utils.jwt.JjwtDeserializer;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.jwt.model.ImmutableJsonWebKey;
import org.eclipse.ditto.jwt.model.JsonWebKey;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Sink;
import akka.util.ByteString;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;

/**
 * Implementation of {@link PublicKeyProvider}. This provider requests keys at the {@link SubjectIssuer} and caches
 * responses to reduce network io.
 */
public final class DittoPublicKeyProvider implements PublicKeyProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(DittoPublicKeyProvider.class);

    private static final String OPENID_CONNECT_DISCOVERY_PATH = "/.well-known/openid-configuration";
    private static final JsonFieldDefinition<String> JSON_JWKS_URI = JsonFieldDefinition.ofString("jwks_uri");

    // Supported curve types described in the RFC https://www.rfc-editor.org/rfc/rfc7518#section-6.2.1.1
    // Mapping of curve type to ECGenParameterSpec input
    private static final Map<String, String> CURVE_TYPE_MAP = Map.of(
            "P-256", "secp256r1",
            "P-384", "secp384r1",
            "P-521", "secp521r1"
    );

    private final JwtSubjectIssuersConfig jwtSubjectIssuersConfig;
    private final HttpClientFacade httpClient;
    private final Materializer materializer;
    private final OAuthConfig oAuthConfig;
    private final Cache<PublicKeyIdWithIssuer, PublicKeyWithParser> publicKeyCache;

    private DittoPublicKeyProvider(final JwtSubjectIssuersConfig jwtSubjectIssuersConfig,
            final HttpClientFacade httpClient,
            final CacheConfig publicKeysConfig,
            final String cacheName,
            final OAuthConfig oAuthConfig) {

        this.jwtSubjectIssuersConfig = argumentNotNull(jwtSubjectIssuersConfig);
        this.httpClient = argumentNotNull(httpClient);
        materializer = SystemMaterializer.get(httpClient::getActorSystem).materializer();
        this.oAuthConfig = oAuthConfig;
        argumentNotNull(publicKeysConfig, "config of the public keys cache");
        argumentNotNull(cacheName);

        final AsyncCacheLoader<PublicKeyIdWithIssuer, PublicKeyWithParser> loader = this::loadPublicKeyWithParser;

        final Caffeine<PublicKeyIdWithIssuer, PublicKeyWithParser> caffeine = Caffeine.newBuilder()
                .maximumSize(publicKeysConfig.getMaximumSize())
                .expireAfterWrite(publicKeysConfig.getExpireAfterWrite())
                .removalListener(new CacheRemovalListener());

        publicKeyCache = CaffeineCache.of(caffeine, loader, cacheName);
    }

    DittoPublicKeyProvider(final JwtSubjectIssuersConfig jwtSubjectIssuersConfig,
            final HttpClientFacade httpClient,
            final OAuthConfig oAuthConfig,
            final Function<AsyncCacheLoader<PublicKeyIdWithIssuer, PublicKeyWithParser>,
                    Cache<PublicKeyIdWithIssuer, PublicKeyWithParser>> publicKeyCacheFactory) {

        this.jwtSubjectIssuersConfig = argumentNotNull(jwtSubjectIssuersConfig);
        this.httpClient = argumentNotNull(httpClient);
        materializer = SystemMaterializer.get(httpClient::getActorSystem).materializer();
        this.oAuthConfig = oAuthConfig;
        publicKeyCache = publicKeyCacheFactory.apply(this::loadPublicKeyWithParser);
    }

    /**
     * Returns a new {@code PublicKeyProvider} for the given parameters.
     *
     * @param jwtSubjectIssuersConfig the configuration of supported JWT subject issuers
     * @param httpClient the http client.
     * @param publicKeysCacheConfig the config of the public keys cache.
     * @param cacheName The name of the cache.
     * @param oAuthConfig the OAuth configuration.
     * @return the PublicKeyProvider.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PublicKeyProvider of(final JwtSubjectIssuersConfig jwtSubjectIssuersConfig,
            final HttpClientFacade httpClient,
            final CacheConfig publicKeysCacheConfig,
            final String cacheName,
            final OAuthConfig oAuthConfig) {

        return new DittoPublicKeyProvider(jwtSubjectIssuersConfig, httpClient, publicKeysCacheConfig, cacheName,
                oAuthConfig);
    }

    @Override
    public CompletableFuture<Optional<PublicKeyWithParser>> getPublicKeyWithParser(final String issuer,
            final String keyId) {
        argumentNotNull(issuer);
        argumentNotNull(keyId);

        return publicKeyCache.get(PublicKeyIdWithIssuer.of(keyId, issuer));
    }

    /* this method is used to asynchronously load the public key into the cache */
    private CompletableFuture<PublicKeyWithParser> loadPublicKeyWithParser(
            final PublicKeyIdWithIssuer publicKeyIdWithIssuer,
            final Executor executor) {

        final String issuer = publicKeyIdWithIssuer.getIssuer();
        final String keyId = publicKeyIdWithIssuer.getKeyId();
        LOGGER.debug("Loading public key with id <{}> from issuer <{}>.", keyId, issuer);

        final Optional<JwtSubjectIssuerConfig> subjectIssuerConfigOpt = jwtSubjectIssuersConfig
                .getConfigItem(issuer);
        if (subjectIssuerConfigOpt.isEmpty()) {
            LOGGER.info("The JWT issuer <{}> is not included in Ditto's gateway configuration at " +
                            "'ditto.gateway.authentication.oauth.openid-connect-issuers', supported are: <{}>",
                    issuer, jwtSubjectIssuersConfig);

            return CompletableFuture
                    .failedFuture(GatewayJwtIssuerNotSupportedException.newBuilder(issuer).build());
        }

        final String issuerWithoutProtocol = issuer.replaceFirst("http(s)?://", "");
        final String discoveryEndpoint = getDiscoveryEndpoint(subjectIssuerConfigOpt.get().getIssuers()
                .stream()
                .filter(configuredIssuer -> configuredIssuer.equals(issuerWithoutProtocol))
                .findAny()
                .orElse(issuerWithoutProtocol)
        );
        final CompletionStage<HttpResponse> responseFuture = getPublicKeysFromDiscoveryEndpoint(discoveryEndpoint);
        final CompletionStage<JsonArray> publicKeysFuture = responseFuture.thenCompose(this::mapResponseToJsonArray);

        return publicKeysFuture.thenApply(publicKeysArray -> mapToPublicKey(publicKeysArray, keyId, discoveryEndpoint))
                .thenApply(optionalKey -> optionalKey.map(this::mapToPublicKeyWithParser).orElse(null))
                .toCompletableFuture();
    }

    private String getDiscoveryEndpoint(final String issuerWithoutProtocolPrefix) {
        final String iss;
        if (issuerWithoutProtocolPrefix.endsWith("/")) {
            iss = issuerWithoutProtocolPrefix.substring(0, issuerWithoutProtocolPrefix.length() - 1);
        } else {
            iss = issuerWithoutProtocolPrefix;
        }

        return jwtSubjectIssuersConfig.getProtocolPrefix() + iss + OPENID_CONNECT_DISCOVERY_PATH;
    }

    private CompletableFuture<JsonArray> mapResponseToJsonArray(final HttpResponse response) {
        final CompletionStage<JsonObject> body = mapResponseToJsonObject(response);
        final JsonPointer keysPointer = JsonPointer.of("keys");

        return body.toCompletableFuture()
                .thenApply(jsonObject -> jsonObject.getValue(keysPointer).map(JsonValue::asArray)
                        .orElseThrow(() -> new JsonMissingFieldException(keysPointer)))
                .exceptionally(t -> {
                    final String message =
                            MessageFormat.format("Failed to extract public keys from JSON response <{0}>", body);
                    LOGGER.warn(message, t);
                    throw PublicKeyProviderUnavailableException.newBuilder()
                            .cause(new IllegalStateException(message, t))
                            .build();
                });
    }

    private CompletionStage<HttpResponse> getPublicKeysFromDiscoveryEndpoint(final String discoveryEndpoint) {
        LOGGER.debug("Loading public keys from discovery endpoint <{}>.", discoveryEndpoint);

        return httpClient.createSingleHttpRequest(HttpRequest.GET(discoveryEndpoint))
                .thenCompose(this::mapResponseToJsonObject)
                .thenApply(jsonObject -> jsonObject.getValueOrThrow(JSON_JWKS_URI))
                .thenCompose(jwksUri -> httpClient.createSingleHttpRequest(HttpRequest.GET(jwksUri)))
                .exceptionally(e -> {
                    throw DittoRuntimeException.asDittoRuntimeException(e,
                            cause -> handleUnexpectedException(cause, discoveryEndpoint));
                });
    }

    private CompletionStage<JsonObject> mapResponseToJsonObject(final HttpResponse response) {
        if (!response.status().isSuccess()) {
            handleNonSuccessResponse(response);
        }

        return response.entity().getDataBytes().fold(ByteString.emptyByteString(), ByteString::concat)
                .map(ByteString::utf8String)
                .map(JsonFactory::readFrom)
                .map(JsonValue::asObject)
                .runWith(Sink.head(), materializer);
    }

    private void handleNonSuccessResponse(final HttpResponse response) {
        getBodyAsString(response)
                .thenAccept(stringBody -> LOGGER.info(
                        "Got non success response from public key provider with status code: <{}> and body: <{}>.",
                        response.status(), stringBody));

        throw GatewayAuthenticationProviderUnavailableException.newBuilder()
                .message("Got unexpected response from public key provider.")
                .build();
    }

    private CompletionStage<String> getBodyAsString(final HttpResponse response) {
        return response.entity().getDataBytes().fold(ByteString.emptyByteString(), ByteString::concat)
                .map(ByteString::utf8String)
                .runWith(Sink.head(), materializer);
    }

    private static PublicKeyProviderUnavailableException handleUnexpectedException(final Throwable e,
            final String discoveryEndpoint) {

        final String msg = MessageFormat.format("Got Exception from discovery endpoint <{0}>.", discoveryEndpoint);
        LOGGER.warn(msg, e);

        return PublicKeyProviderUnavailableException.newBuilder().cause(e).build();
    }

    private static Optional<PublicKey> mapToPublicKey(final JsonArray publicKeys, final String keyId,
            final String discoveryEndpoint) {

        LOGGER.debug("Trying to find JSON Web Key with id <{}> in json array <{}>.", keyId, publicKeys);

        for (final JsonValue jsonValue : publicKeys) {
            try {
                final JsonObject jsonObject = jsonValue.asObject();
                final JsonWebKey jsonWebKey = ImmutableJsonWebKey.fromJson(jsonObject);

                if (jsonWebKey.getId().equals(keyId)) {
                    LOGGER.debug("Found matching Json Web Key for id <{}>: <{}>.", keyId, jsonWebKey);
                    return Optional.of(mapMatchingPublicKey(discoveryEndpoint, jsonWebKey));
                }
            } catch (final NoSuchAlgorithmException | InvalidKeySpecException | InvalidParameterSpecException |
                           JsonMissingFieldException e) {
                final String msg =
                        MessageFormat.format("Got invalid JSON Web Key from JwkResource provider at discovery " +
                                "endpoint <{0}>", discoveryEndpoint);

                throw getJwkInvalidExceptionException(msg, e);
            }
        }

        LOGGER.debug("Did not find key with id <{}>.", keyId);
        return Optional.empty();
    }

    private static PublicKey mapMatchingPublicKey(final String discoveryEndpoint, final JsonWebKey jsonWebKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidParameterSpecException,
            JsonMissingFieldException, JwkInvalidException {

        final var type = jsonWebKey.getType();
        final KeyFactory keyFactory = KeyFactory.getInstance(type);
        final PublicKey result;
        if ("EC".equals(type)) {
            result = generateECPublicKey(keyFactory, jsonWebKey, discoveryEndpoint);
        } else if ("RSA".equals(type)) {
            result = generateRSAPublicKey(keyFactory, jsonWebKey);
        } else {
            final String msg = MessageFormat.format(
                    "Got invalid JSON Web Key from JwkResource provider at discovery endpoint <{0}>. " +
                            "The KeyType (kty): <{1}> is unknown.", discoveryEndpoint, type);

            throw getJwkInvalidExceptionException(msg, null);
        }

        return result;
    }

    private static PublicKey generateECPublicKey(final KeyFactory keyFactory, final JsonWebKey jsonWebKey,
            final String discoveryEndpoint)
            throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidParameterSpecException,
            JsonMissingFieldException, JwkInvalidException {

        final AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        final var curveType = jsonWebKey.getCurveType()
                .orElseThrow(() -> new JsonMissingFieldException(JsonWebKey.JsonFields.KEY_CURVE.getPointer()));
        final var stdName = CURVE_TYPE_MAP.get(curveType);
        if (stdName == null) {
            final String msg = MessageFormat.format(
                    "Got invalid JSON Web Key from JwkResource provider at discovery endpoint <{0}>. " +
                            "The curve parameter (crv): <{1}> is not supported.", discoveryEndpoint, curveType);

            throw getJwkInvalidExceptionException(msg, null);
        }
        parameters.init(new ECGenParameterSpec(stdName));
        final ECParameterSpec ecParameterSpec = parameters.getParameterSpec(ECParameterSpec.class);
        final var xCoordinate = jsonWebKey.getXCoordinate()
                .orElseThrow(() -> new JsonMissingFieldException(JsonWebKey.JsonFields.KEY_X_COORDINATE.getPointer()));
        final var yCoordinate = jsonWebKey.getYCoordinate()
                .orElseThrow(() -> new JsonMissingFieldException(JsonWebKey.JsonFields.KEY_Y_COORDINATE.getPointer()));
        final var ecPoint = new ECPoint(xCoordinate, yCoordinate);
        final var ecPublicKeySpec = new ECPublicKeySpec(ecPoint, ecParameterSpec);

        return keyFactory.generatePublic(ecPublicKeySpec);
    }

    private static PublicKey generateRSAPublicKey(final KeyFactory keyFactory, final JsonWebKey jsonWebKey)
            throws InvalidKeySpecException, JsonMissingFieldException {

        final BigInteger modulus = jsonWebKey.getModulus()
                .orElseThrow(() -> new JsonMissingFieldException(JsonWebKey.JsonFields.KEY_MODULUS.getPointer()));
        final BigInteger exponent = jsonWebKey.getExponent()
                .orElseThrow(() -> new JsonMissingFieldException(JsonWebKey.JsonFields.KEY_EXPONENT.getPointer()));
        final KeySpec rsaPublicKeySpec = new RSAPublicKeySpec(modulus, exponent);

        return keyFactory.generatePublic(rsaPublicKeySpec);
    }

    private static JwkInvalidException getJwkInvalidExceptionException(final String msg,
            @Nullable Throwable e) {

        final JwkInvalidException result;
        if (null != e) {
            LOGGER.warn(msg, e);
            result = JwkInvalidException.newBuilder()
                    .message(msg)
                    .description(e.getMessage())
                    .cause(e)
                    .build();
        } else {
            LOGGER.warn(msg);
            result = JwkInvalidException.newBuilder()
                    .message(msg)
                    .build();
        }

        return result;
    }

    private PublicKeyWithParser mapToPublicKeyWithParser(final PublicKey publicKey) {
        final var jwtParserBuilder = Jwts.parserBuilder();
        final JwtParser jwtParser = jwtParserBuilder.deserializeJsonWith(JjwtDeserializer.getInstance())
                .setSigningKey(publicKey)
                .setAllowedClockSkewSeconds(oAuthConfig.getAllowedClockSkew().getSeconds())
                .build();

        return new PublicKeyWithParser(publicKey, jwtParser);
    }

    private static final class CacheRemovalListener
            implements RemovalListener<PublicKeyIdWithIssuer, PublicKeyWithParser> {

        @Override
        public void onRemoval(@Nullable final PublicKeyIdWithIssuer key, @Nullable final PublicKeyWithParser value,
                @Nonnull final com.github.benmanes.caffeine.cache.RemovalCause cause) {

            final String msgTemplate = "Removed PublicKey with ID <{}> from cache due to cause '{}'.";
            LOGGER.debug(msgTemplate, key, cause);
        }

    }

}
