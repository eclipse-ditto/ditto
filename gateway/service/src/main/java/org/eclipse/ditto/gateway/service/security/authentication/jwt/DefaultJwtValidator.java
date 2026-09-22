/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import java.text.MessageFormat;
import java.util.concurrent.CompletableFuture;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.base.model.common.BinaryValidationResult;
import org.eclipse.ditto.gateway.api.GatewayAuthenticationFailedException;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.JwtParser;

/**
 * Default implementation of {@link JwtValidator}.
 */
@ThreadSafe
public final class DefaultJwtValidator implements JwtValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultJwtValidator.class);

    private final PublicKeyProvider publicKeyProvider;

    private DefaultJwtValidator(final PublicKeyProvider publicKeyProvider) {
        this.publicKeyProvider = publicKeyProvider;
    }

    /**
     * Creates a new {@code JwtValidator} instance.
     *
     * @param publicKeyProvider provider for public keys of jwt issuers.
     * @return the instance.
     */
    public static JwtValidator of(final PublicKeyProvider publicKeyProvider) {
        return new DefaultJwtValidator(publicKeyProvider);
    }

    @Override
    public CompletableFuture<BinaryValidationResult> validate(final JsonWebToken jsonWebToken) {
        final String issuer;
        final String keyId;
        try {
            issuer = jsonWebToken.getIssuer();
            keyId = jsonWebToken.getKeyId();
        } catch (final JsonRuntimeException e) {
            // A token that lacks the mandatory "iss"/"kid" claims (or whose header/body claims are otherwise
            // unreadable) is a client-side authentication failure, not a server-side outage. Extracting these
            // claims eagerly throws a JsonRuntimeException (e.g. JsonMissingFieldException) which is not a
            // DittoRuntimeException, so without this guard it would bubble up to the generic
            // "authentication provider unavailable" fallback and be reported as HTTP 503 instead of 401.
            LOGGER.info("Failed to read the JWT's 'iss'/'kid' claims due to <{}> with message: <{}>.",
                    e.getClass().getSimpleName(), e.getMessage());
            final Exception exception = GatewayAuthenticationFailedException
                    .newBuilder("The JWT is missing the required 'iss' and/or 'kid' claim.")
                    .description(e.getMessage())
                    .cause(e)
                    .build();

            return CompletableFuture.completedFuture(BinaryValidationResult.invalid(exception));
        }

        return publicKeyProvider.getPublicKeyWithParser(issuer, keyId)
                .thenApply(publicKeyWithParserOpt -> publicKeyWithParserOpt
                        .map(publicKeyWithParser -> tryToValidateWithJwtParser(jsonWebToken,
                                publicKeyWithParser.getJwtParser()))
                        .orElseGet(() -> {
                            final var msgPattern = "Public Key of issuer <{0}> with key ID <{1}> not found!";
                            final var msg = MessageFormat.format(msgPattern, issuer, keyId);
                            final Exception exception = GatewayAuthenticationFailedException.newBuilder(msg).build();

                            return BinaryValidationResult.invalid(exception);
                        }));
    }

    private BinaryValidationResult tryToValidateWithJwtParser(final JsonWebToken jsonWebToken,
            final JwtParser jwtParser) {
        try {
            return validateWithJwtParser(jsonWebToken, jwtParser);
        } catch (final Exception e) {
            LOGGER.info("Failed to parse/validate JWT due to <{}> with message: <{}>", e.getClass().getSimpleName(),
                    e.getMessage());

            return BinaryValidationResult.invalid(e);
        }
    }

    private BinaryValidationResult validateWithJwtParser(final JsonWebToken jsonWebToken, final JwtParser jwtParser) {
        jwtParser.parse(jsonWebToken.getToken());

        return BinaryValidationResult.valid();
    }

}
