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
        final var issuer = jsonWebToken.getIssuer();
        final var keyId = jsonWebToken.getKeyId();

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
        jwtParser.parseClaimsJws(jsonWebToken.getToken());

        return BinaryValidationResult.valid();
    }

}
