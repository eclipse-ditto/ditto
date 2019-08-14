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

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.FieldType;

/**
 * Representation for a JSON Web Token.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7519">JSON Web Token (JWT)</a>
 */
public interface JsonWebToken {

    /**
     * Returns the encoded token.
     *
     * @return the token.
     */
    String getToken();

    /**
     * Returns the header JSON object of the token.
     *
     * @return the header JSON.
     */
    JsonObject getHeader();

    /**
     * Returns the body JSON object of the token.
     *
     * @return the body JSON.
     */
    JsonObject getBody();

    /**
     * Returns identifier of the key.
     *
     * @return the identifier.
     */
    String getKeyId();

    /**
     * Returns the issuer of the token.
     *
     * @return the issuer.
     */
    String getIssuer();

    /**
     * Returns the signature of the token.
     *
     * @return the signature.
     */
    String getSignature();

    /**
     * Returns the subjects which can be used for authorization, e.g. the "sub" claim.
     *
     * @return the subjects.
     */
    List<String> getSubjects();

    /**
     * Returns the audience of the token.
     *
     * @return the audience.
     */
    Audience getAudience();

    /**
     * Returns the scopes of the token.
     *
     * @return the scopes.
     */
    Set<String> getScopes();

    /**
     * Checks if this JSON web token is valid in terms of not expired, well formed and correctly signed.
     *
     * @param publicKeyProvider the public key provider to provide the public key that should be used to sign this JSON
     * web token.
     * @return A Future resolving to a {@link BinaryValidationResult}.
     */
    CompletableFuture<BinaryValidationResult> validate(PublicKeyProvider publicKeyProvider);

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a JSON Web Token.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the key ID.
         */
        public static final JsonFieldDefinition<String> KEY_ID =
                JsonFactory.newStringFieldDefinition("kid", FieldType.REGULAR);

        /**
         * JSON field containing the issuer.
         */
        public static final JsonFieldDefinition<String> ISSUER =
                JsonFactory.newStringFieldDefinition("iss", FieldType.REGULAR);

        /**
         * JSON field containing the user id.
         */
        public static final JsonFieldDefinition<String> USER_ID =
                JsonFactory.newStringFieldDefinition("sub", FieldType.REGULAR);

        /**
         * JSON field containing the audience.
         */
        public static final JsonFieldDefinition<JsonValue> AUDIENCE =
                JsonFactory.newJsonValueFieldDefinition("aud", FieldType.REGULAR);

        /**
         * JSON field containing the scope.
         */
        public static final JsonFieldDefinition<String> SCOPE =
                JsonFactory.newStringFieldDefinition("scope", FieldType.REGULAR);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
