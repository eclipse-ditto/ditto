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
package org.eclipse.ditto.services.gateway.security.authentication.jwt;

import java.security.Key;
import java.text.MessageFormat;
import java.util.concurrent.CompletableFuture;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.model.base.common.BinaryValidationResult;
import org.eclipse.ditto.model.jwt.JsonWebToken;
import org.eclipse.ditto.services.utils.jwt.JjwtDeserializer;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;

/**
 * Default implementation of {@link org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtValidator}.
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
        final String issuer = jsonWebToken.getIssuer();
        final String keyId = jsonWebToken.getKeyId();
        return publicKeyProvider.getPublicKey(issuer, keyId)
                .thenApply(publicKeyOpt -> publicKeyOpt
                        .map(publicKey -> tryToValidateWithPublicKey(jsonWebToken, publicKey))
                        .orElseGet(() -> {
                            final String msgPattern = "Public Key of issuer <{0}> with key ID <{1}> not found!";
                            final String msg = MessageFormat.format(msgPattern, issuer, keyId);
                            final Exception exception = GatewayAuthenticationFailedException.newBuilder(msg).build();
                            return BinaryValidationResult.invalid(exception);
                        }));
    }

    private BinaryValidationResult tryToValidateWithPublicKey(final JsonWebToken jsonWebToken, final Key publicKey) {
        try {
            return validateWithPublicKey(jsonWebToken, publicKey);
        } catch (final Exception e) {
            LOGGER.info("Failed to parse JWT!", e);
            return BinaryValidationResult.invalid(e);
        }
    }

    @SuppressWarnings("unchecked")
    private BinaryValidationResult validateWithPublicKey(final JsonWebToken jsonWebToken, final Key publicKey) {
        final JwtParser jwtParser = Jwts.parser();
        jwtParser.deserializeJsonWith(JjwtDeserializer.getInstance())
                .setSigningKey(publicKey)
                .parse(jsonWebToken.getToken());

        return BinaryValidationResult.valid();
    }

}
