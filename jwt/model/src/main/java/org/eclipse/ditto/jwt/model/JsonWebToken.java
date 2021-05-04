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
package org.eclipse.ditto.jwt.model;

import java.time.Instant;
import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.json.FieldType;

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
     * Returns the authorized party of the token.
     *
     * @return the authorized party.
     */
    String getAuthorizedParty();

    /**
     * Returns the scopes of the token.
     *
     * @return the scopes.
     */
    List<String> getScopes();

    /**
     * Returns the expiration time of the token.
     *
     * @return the expiration time.
     */
    Instant getExpirationTime();

    /**
     * Returns true if the token is expired.
     *
     * @return true if token is expired otherwise false.
     */
    boolean isExpired();

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a JSON Web Token.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the key identifier.
         */
        public static final JsonFieldDefinition<String> KID =
                JsonFactory.newStringFieldDefinition("kid", FieldType.REGULAR);

        /**
         * JSON field containing the issuer.
         */
        public static final JsonFieldDefinition<String> ISS =
                JsonFactory.newStringFieldDefinition("iss", FieldType.REGULAR);

        /**
         * JSON field containing the subject, e.g. user identifier.
         */
        public static final JsonFieldDefinition<String> SUB =
                JsonFactory.newStringFieldDefinition("sub", FieldType.REGULAR);

        /**
         * JSON field containing the audience.
         */
        public static final JsonFieldDefinition<JsonValue> AUD =
                JsonFactory.newJsonValueFieldDefinition("aud", FieldType.REGULAR);

        /**
         * JSON field containing the authorized party.
         */
        public static final JsonFieldDefinition<String> AZP =
                JsonFactory.newStringFieldDefinition("azp", FieldType.REGULAR);

        /**
         * JSON field containing the scope.
         */
        public static final JsonFieldDefinition<String> SCOPE =
                JsonFactory.newStringFieldDefinition("scope", FieldType.REGULAR);

        /**
         * JSON field containing the expiration time.
         */
        public static final JsonFieldDefinition<Long> EXP =
                JsonFactory.newLongFieldDefinition("exp", FieldType.REGULAR);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
