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

import java.security.Key;
import java.text.MessageFormat;
import java.util.concurrent.CompletableFuture;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.base.model.common.BinaryValidationResult;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayAuthenticationFailedException;
import org.eclipse.ditto.gateway.service.util.config.security.OAuthConfig;
import org.eclipse.ditto.internal.utils.jwt.JjwtDeserializer;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Jwts;

/**
 * Default implementation of {@link JwtValidator}.
 */
@ThreadSafe
public final class DefaultJwtValidator implements JwtValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultJwtValidator.class);

    private final PublicKeyProvider publicKeyProvider;
    private final OAuthConfig oAuthConfig;

    private DefaultJwtValidator(final PublicKeyProvider publicKeyProvider, final OAuthConfig oAuthConfig) {
        this.publicKeyProvider = publicKeyProvider;
        this.oAuthConfig = oAuthConfig;
    }

    /**
     * Creates a new {@code JwtValidator} instance.
     *
     * @param publicKeyProvider provider for public keys of jwt issuers.
     * @param oAuthConfig the OAuth config.
     * @return the instance.
     */
    public static JwtValidator of(final PublicKeyProvider publicKeyProvider, final OAuthConfig oAuthConfig) {
        return new DefaultJwtValidator(publicKeyProvider, oAuthConfig);
    }

    @Override
    public CompletableFuture<BinaryValidationResult> validate(final JsonWebToken jsonWebToken) {
        final var issuer = jsonWebToken.getIssuer();
        final var keyId = jsonWebToken.getKeyId();

        return publicKeyProvider.getPublicKey(issuer, keyId)
                .thenApply(publicKeyOpt -> publicKeyOpt
                        .map(publicKey -> tryToValidateWithPublicKey(jsonWebToken, publicKey))
                        .orElseGet(() -> {
                            final var msgPattern = "Public Key of issuer <{0}> with key ID <{1}> not found!";
                            final var msg = MessageFormat.format(msgPattern, issuer, keyId);
                            final Exception exception = GatewayAuthenticationFailedException.newBuilder(msg).build();

                            return BinaryValidationResult.invalid(exception);
                        }));
    }

    private BinaryValidationResult tryToValidateWithPublicKey(final JsonWebToken jsonWebToken, final Key publicKey) {
        try {
            return validateWithPublicKey(jsonWebToken, publicKey);
        } catch (final Exception e) {
            LOGGER.info("Failed to parse/validate JWT due to <{}> with message: <{}>", e.getClass().getSimpleName(),
                    e.getMessage());

            return BinaryValidationResult.invalid(e);
        }
    }

    private BinaryValidationResult validateWithPublicKey(final JsonWebToken jsonWebToken, final Key publicKey) {
        final var jwtParserBuilder = Jwts.parserBuilder();
        jwtParserBuilder.deserializeJsonWith(JjwtDeserializer.getInstance())
                .setSigningKey(publicKey)
                .setAllowedClockSkewSeconds(oAuthConfig.getAllowedClockSkew().getSeconds())
                .build()
                .parseClaimsJws(jsonWebToken.getToken());

        return BinaryValidationResult.valid();
    }

}
