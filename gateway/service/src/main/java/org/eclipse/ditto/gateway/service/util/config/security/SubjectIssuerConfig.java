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
     * Returns the issuer endpoint.
     *
     * @return the token issuer endpoint.
     */
    String getIssuer();

    /**
     * Returns the authorization subject templates.
     *
     * @return a list of templates.
     */
    List<String> getAuthorizationSubjectTemplates();


    enum SubjectIssuerConfigValue implements KnownConfigValue {
        ISSUER("issuer", ""),
        AUTH_SUBJECTS("auth-subjects", List.of("{{jwt:sub}}"));

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
