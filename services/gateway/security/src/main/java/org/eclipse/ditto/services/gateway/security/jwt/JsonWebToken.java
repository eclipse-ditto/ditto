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

import static org.eclipse.ditto.json.JsonFactory.newFieldDefinition;

import java.util.List;

import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
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
     * Returns the header JSON object from the JWT.
     *
     * @return the header JSON.
     */
    JsonObject getHeader();

    /**
     * Returns the body JSON object from the JWT.
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
     * Returns the authorization subjects contained in the {@code JsonWebToken} prefixed with {@code SubjectType}.
     *
     * @return the authorization subjects.
     */
    List<AuthorizationSubject> getAuthorizationSubjects();

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a JSON Web Token.
     */
    final class JsonFields {

        /**
         * JSON field containing the key id.
         */
        public static final JsonFieldDefinition KEY_ID = newFieldDefinition("kid", String.class, FieldType.REGULAR);

        /**
         * JSON field containing the issuer.
         */
        public static final JsonFieldDefinition ISSUER = newFieldDefinition("iss", String.class, FieldType.REGULAR);

        /**
         * JSON field containing the user id.
         */
        public static final JsonFieldDefinition USER_ID = newFieldDefinition("sub", String.class, FieldType.REGULAR);
    }

}
