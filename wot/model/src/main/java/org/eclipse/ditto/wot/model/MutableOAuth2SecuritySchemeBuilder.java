/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.model;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Mutable builder for {@link OAuth2SecurityScheme}s.
 */
final class MutableOAuth2SecuritySchemeBuilder
        extends AbstractSecuritySchemeBuilder<OAuth2SecurityScheme.Builder, OAuth2SecurityScheme>
        implements OAuth2SecurityScheme.Builder {

    MutableOAuth2SecuritySchemeBuilder(final String securitySchemeName,
            final JsonObjectBuilder wrappedObjectBuilder) {
        super(securitySchemeName, wrappedObjectBuilder, MutableOAuth2SecuritySchemeBuilder.class);
    }

    @Override
    SecuritySchemeScheme getSecuritySchemeScheme() {
        return SecuritySchemeScheme.OAUTH2;
    }

    @Override
    public OAuth2SecurityScheme.Builder setAuthorization(@Nullable final IRI authorization) {
        if (authorization != null) {
            putValue(OAuth2SecurityScheme.JsonFields.AUTHORIZATION, authorization.toString());
        } else {
            remove(OAuth2SecurityScheme.JsonFields.AUTHORIZATION);
        }
        return myself;
    }

    @Override
    public OAuth2SecurityScheme.Builder setToken(@Nullable final IRI token) {
        if (token != null) {
            putValue(OAuth2SecurityScheme.JsonFields.TOKEN, token.toString());
        } else {
            remove(OAuth2SecurityScheme.JsonFields.TOKEN);
        }
        return myself;
    }

    @Override
    public OAuth2SecurityScheme.Builder setRefresh(@Nullable final IRI refresh) {
        if (refresh != null) {
            putValue(OAuth2SecurityScheme.JsonFields.REFRESH, refresh.toString());
        } else {
            remove(OAuth2SecurityScheme.JsonFields.REFRESH);
        }
        return myself;
    }

    @Override
    public OAuth2SecurityScheme.Builder setScopes(@Nullable final OAuth2Scopes scopes) {
        if (scopes != null) {
            if (scopes instanceof MultipleOAuth2Scopes) {
                putValue(OAuth2SecurityScheme.JsonFields.SCOPES_MULTIPLE, ((MultipleOAuth2Scopes) scopes).toJson());
            } else if (scopes instanceof SingleOAuth2Scopes) {
                putValue(OAuth2SecurityScheme.JsonFields.SCOPES, scopes.toString());
            } else {
                throw new IllegalArgumentException("Unsupported scopes: " + scopes.getClass().getSimpleName());
            }
        } else {
            remove(OAuth2SecurityScheme.JsonFields.SCOPES);
        }
        return myself;
    }

    @Override
    public OAuth2SecurityScheme.Builder setFlow(@Nullable final String flow) {
        putValue(OAuth2SecurityScheme.JsonFields.FLOW, flow);
        return myself;
    }

    @Override
    public OAuth2SecurityScheme build() {
        return new ImmutableOAuth2SecurityScheme(securitySchemeName, wrappedObjectBuilder.build());
    }
}
