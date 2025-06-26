/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import java.util.Optional;

import org.eclipse.ditto.json.JsonFieldDefinition;

/**
 * Common functionality of OAuth credentials.
 *
 * @since 3.8.0
 */
public interface OAuthCredentials extends Credentials {

    /**
     * @return the token endpoint
     */
    String getTokenEndpoint();

    /**
     * @return the client id
     */
    String getClientId();

    /**
     * @return the client secret
     */
    Optional<String> getClientSecret();

    /**
     * @return the scope
     */
    String getRequestedScopes();

    /**
     * @return the audience
     */
    Optional<String> getAudience();

    /**
     * JSON field definitions.
     */
    class JsonFields extends Credentials.JsonFields {

        /**
         * JSON field definition of OAuth token endpoint.
         */
        public static final JsonFieldDefinition<String> TOKEN_ENDPOINT = JsonFieldDefinition.ofString("tokenEndpoint");

        /**
         * JSON field definition of client ID.
         */
        public static final JsonFieldDefinition<String> CLIENT_ID = JsonFieldDefinition.ofString("clientId");

        /**
         * JSON field definition of client secret.
         */
        public static final JsonFieldDefinition<String> CLIENT_SECRET = JsonFieldDefinition.ofString("clientSecret");

        /**
         * JSON field definition of the requested scopes.
         */
        public static final JsonFieldDefinition<String> REQUESTED_SCOPES = JsonFieldDefinition.ofString(
                "requestedScopes");

        /**
         * JSON field definition of the audience.
         */
        public static final JsonFieldDefinition<String> AUDIENCE = JsonFieldDefinition.ofString(
                "audience");
    }
}
