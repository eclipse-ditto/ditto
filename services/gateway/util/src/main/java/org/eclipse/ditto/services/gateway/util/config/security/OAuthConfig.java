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
package org.eclipse.ditto.services.gateway.util.config.security;

import java.util.Collections;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Provides configuration settings for OAuth.
 */
@Immutable
@AllValuesAreNonnullByDefault
public interface OAuthConfig {

    /**
     * Returns the protocol to access all OAuth endpoints.
     *
     * @return the protocol with which to access all OAuth endpoints.
     */
    String getProtocol();

    /**
     * Returns all supported openid connect issuers.
     *
     * @return the issuers.
     */
    Map<SubjectIssuer, String> getOpenIdConnectIssuers();

    /**
     * Returns all additionally supported openid connect issuers. This can be useful during migration phases e.g. if
     * you have multiple issuer URIs for the same subject issuer.
     *
     * @return the additional issuers.
     */
    Map<SubjectIssuer, String> getOpenIdConnectIssuersExtension();

    /**
     * Returns the template of the subject activated via token integration. May contain placeholders.
     *
     * @return the token integration subject.
     */
    String getTokenIntegrationSubject();

    enum OAuthConfigValue implements KnownConfigValue {
        PROTOCOL("protocol", "https"),
        OPENID_CONNECT_ISSUERS("openid-connect-issuers", Collections.emptyMap()),
        OPENID_CONNECT_ISSUERS_EXTENSION("openid-connect-issuers-extension", Collections.emptyMap()),
        TOKEN_INTEGRATION_SUBJECT("token-integration-subject", "integration:{{policy-entry:label}}:{{jwt:aud}}");

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
