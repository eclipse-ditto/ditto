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

import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;

/**
 * Representation for a JSON Web Token.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7519">JSON Web Token (UNKNOWN)</a>
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
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a JSON Web Token.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the key id.
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

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
