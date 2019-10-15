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
package org.eclipse.ditto.services.gateway.security.config;

import java.util.Collections;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for OAuth.
 */
@Immutable
public interface OAuthConfig {

    /**
     * Returns all supported openid connect issuers.
     *
     * @return the issuers.
     */
    Map<SubjectIssuer, String> getOpenIdConnectIssuers();

    enum OAuthConfigValue implements KnownConfigValue {
        OPENID_CONNECT_ISSUERS("openid-connect-issuers", Collections.emptyMap());

        private final String path;
        private final Object defaultValue;

        OAuthConfigValue(final String thePath, final Object theDefaultValue) {
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
