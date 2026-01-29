/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.util.config.security;

import java.util.List;
import java.util.Map;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Represents configuration for a {@link org.eclipse.ditto.policies.model.SubjectIssuer}, containing
 * issuer endpoint and a list of templates for substituting authorization
 * subjects.
 *
 * @since 2.0.0
 */
public interface SubjectIssuerConfig {

    /**
     * Returns the issuer endpoints.
     *
     * @return the token issuer endpoints.
     */
    List<String> getIssuers();

    /**
     * Returns the authorization subject templates.
     *
     * @return a list of templates.
     */
    List<String> getAuthorizationSubjectTemplates();

    /**
     * Returns claims of a token to inject into DittoHeaders (using the map key as key for the custom header to inject).
     *
     * @return claims of a token to inject into DittoHeaders (using the map key as key for the custom header to inject).
     */
    Map<String, String> getInjectClaimsIntoHeaders();

    /**
     * Returns the prerequisite conditions that must be met for a JWT to be accepted.
     * These conditions are evaluated as placeholder expressions. If any condition resolves to an empty value,
     * the JWT is rejected with a 401 response.
     *
     * @return a list of prerequisite condition expressions.
     * @since 3.9.0
     */
    List<String> getPrerequisiteConditions();


    enum SubjectIssuerConfigValue implements KnownConfigValue {
        ISSUER("issuer", ""),
        ISSUERS("issuers", List.of()),
        AUTH_SUBJECTS("auth-subjects", List.of("{{jwt:sub}}")),
        INJECT_CLAIMS_INTO_HEADERS("inject-claims-into-headers", Map.of()),
        PREREQUISITE_CONDITIONS("prerequisite-conditions", List.of());

        private final String path;
        private final Object defaultValue;

        SubjectIssuerConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }
    }
}
