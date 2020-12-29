/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.gateway.endpoints.routes.policies;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.jwt.JsonWebToken;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtPlaceholder;
import org.eclipse.ditto.services.gateway.util.config.security.OAuthConfig;

/**
 * Creator of token integration subjects for the configured OAuth subject pattern.
 */
public final class OAuthTokenIntegrationSubjectIdFactory implements TokenIntegrationSubjectIdFactory {

    private final String subjectTemplate;

    private OAuthTokenIntegrationSubjectIdFactory(final String subjectTemplate) {
        this.subjectTemplate = subjectTemplate;
    }

    /**
     * Create an OAuth token integration subject ID factory from OAuth config.
     *
     * @param oAuthConfig the config.
     * @return the factory.
     */
    public static OAuthTokenIntegrationSubjectIdFactory of(final OAuthConfig oAuthConfig) {
        return new OAuthTokenIntegrationSubjectIdFactory(oAuthConfig.getTokenIntegrationSubject());
    }

    @Override
    public SubjectId getSubjectId(final DittoHeaders dittoHeaders, final JsonWebToken jwt) {
        final ExpressionResolver expressionResolver = PlaceholderFactory.newExpressionResolver(
                PlaceholderFactory.newPlaceholderResolver(PlaceholderFactory.newHeadersPlaceholder(), dittoHeaders),
                PlaceholderFactory.newPlaceholderResolver(JwtPlaceholder.getInstance(), jwt)
        );
        return SubjectId.newInstance(expressionResolver.resolvePartially(subjectTemplate));
    }
}
