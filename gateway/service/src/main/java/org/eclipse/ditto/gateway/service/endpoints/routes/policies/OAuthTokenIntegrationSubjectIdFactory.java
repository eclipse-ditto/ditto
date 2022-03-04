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
package org.eclipse.ditto.gateway.service.endpoints.routes.policies;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtPlaceholder;
import org.eclipse.ditto.gateway.service.util.config.security.OAuthConfig;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.policies.model.SubjectId;

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
    public Set<SubjectId> getSubjectIds(final DittoHeaders dittoHeaders, final JsonWebToken jwt) {
        final ExpressionResolver expressionResolver = PlaceholderFactory.newExpressionResolver(
                PlaceholderFactory.newPlaceholderResolver(PlaceholderFactory.newHeadersPlaceholder(), dittoHeaders),
                PlaceholderFactory.newPlaceholderResolver(JwtPlaceholder.getInstance(), jwt)
        );
        return expressionResolver.resolvePartiallyAsPipelineElement(subjectTemplate, Set.of(JwtPlaceholder.PREFIX))
                .toStream()
                .map(SubjectId::newInstance)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

}
