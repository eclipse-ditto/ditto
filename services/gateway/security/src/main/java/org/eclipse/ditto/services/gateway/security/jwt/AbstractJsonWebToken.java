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
package org.eclipse.ditto.services.gateway.security.jwt;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayJwtInvalidException;

/**
 * Abstract implementation of {@link JsonWebToken}.
 */
public abstract class AbstractJsonWebToken implements JsonWebToken {

    /**
     * Delimiter of the authorization string.
     */
    private static final String AUTHORIZATION_DELIMITER = " ";

    /**
     * Delimiter of the JSON Web Token.
     */
    private static final String JWT_DELIMITER = "\\.";

    private final String token;
    private final JsonObject header;
    private final JsonObject body;
    private final String signature;

    protected AbstractJsonWebToken(final String authorizationString) {
        checkNotNull(authorizationString, "Authorization String");
        checkNotEmpty(authorizationString, "Authorization String");

        final String[] authorizationStringSplit = authorizationString.split(AUTHORIZATION_DELIMITER);

        if (authorizationStringSplit.length != 2) {
            throw GatewayAuthenticationFailedException.newBuilder("The UNKNOWN Authorization Header is invalid!")
                    .build();
        }

        final String jwtBase64Encoded = authorizationStringSplit[1];
        final String[] split = jwtBase64Encoded.split(JWT_DELIMITER);

        try {
            final Base64.Decoder decoder = Base64.getDecoder();
            final byte[] headerBytes = decoder.decode(split[0]);
            header = JsonFactory.newObject(new String(headerBytes, StandardCharsets.UTF_8));

            final byte[] bodyBytes = decoder.decode(split[1]);
            body = JsonFactory.newObject(new String(bodyBytes, StandardCharsets.UTF_8));
        } catch (final IllegalArgumentException | JsonParseException e) {
            throw GatewayJwtInvalidException.newBuilder()
                    .description("Check if your UNKNOWN has the correct format and is Base64 URL encoded.")
                    .cause(e)
                    .build();
        }

        token = jwtBase64Encoded;
        signature = split[2];
    }

    protected AbstractJsonWebToken(final JsonWebToken jsonWebToken) {
        checkNotNull(jsonWebToken, "JSON Web Token");

        token = jsonWebToken.getToken();
        header = jsonWebToken.getHeader();
        body = jsonWebToken.getBody();
        signature = jsonWebToken.getSignature();
    }

    @Override
    public JsonObject getHeader() {
        return header;
    }

    @Override
    public JsonObject getBody() {
        return body;
    }

    @Override
    public String getToken() {
        return token;
    }

    @Override
    public String getKeyId() {
        return header.getValueOrThrow(JsonFields.KEY_ID);
    }

    @Override
    public String getIssuer() {
        return body.getValueOrThrow(JsonFields.ISSUER);
    }

    @Override
    public String getSignature() {
        return signature;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractJsonWebToken that = (AbstractJsonWebToken) o;
        return Objects.equals(token, that.token) && Objects.equals(header, that.header) &&
                Objects.equals(body, that.body)
                && Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token, header, body, signature);
    }

    @Override
    public String toString() {
        return "token=" + token + ", header=" + header + ", body=" + body + ", signature=" + signature;
    }

}
