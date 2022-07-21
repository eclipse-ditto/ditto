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

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;

/**
 * Immutable implementation of {@link OAuth2SecurityScheme}.
 */
@Immutable
final class ImmutableOAuth2SecurityScheme extends AbstractSecurityScheme implements OAuth2SecurityScheme {

    ImmutableOAuth2SecurityScheme(final String securitySchemeName, final JsonObject wrappedObject) {
        super(securitySchemeName, wrappedObject);
    }

    @Override
    public Optional<IRI> getAuthorization() {
        return wrappedObject.getValue(JsonFields.AUTHORIZATION)
                .map(IRI::of);
    }

    @Override
    public Optional<IRI> getToken() {
        return wrappedObject.getValue(JsonFields.TOKEN)
                .map(IRI::of);
    }

    @Override
    public Optional<IRI> getRefresh() {
        return wrappedObject.getValue(JsonFields.REFRESH)
                .map(IRI::of);
    }

    @Override
    public Optional<OAuth2Scopes> getScopes() {
        return Optional.ofNullable(
                TdHelpers.getValueIgnoringWrongType(wrappedObject, JsonFields.SCOPES_MULTIPLE)
                        .map(MultipleOAuth2Scopes::fromJson)
                        .map(OAuth2Scopes.class::cast)
                        .orElseGet(() -> wrappedObject.getValue(JsonFields.SCOPES)
                                .map(SingleOAuth2Scopes::of)
                                .orElse(null)
                        )
        );
    }

    @Override
    public Optional<OAuth2Flow> getFlow() {
        return wrappedObject.getValue(JsonFields.FLOW)
                .map(OAuth2Flow::of);
    }

    @Override
    protected SecurityScheme createInstance(final JsonObject newWrapped) {
        return new ImmutableOAuth2SecurityScheme(getSecuritySchemeName(), newWrapped);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImmutableOAuth2SecurityScheme;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }
}
