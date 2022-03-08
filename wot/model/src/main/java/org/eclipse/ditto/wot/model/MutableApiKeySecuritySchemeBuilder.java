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
 * Mutable builder for {@link ApiKeySecurityScheme}s.
 */
final class MutableApiKeySecuritySchemeBuilder
        extends AbstractSecuritySchemeBuilder<ApiKeySecurityScheme.Builder, ApiKeySecurityScheme>
        implements ApiKeySecurityScheme.Builder {

    MutableApiKeySecuritySchemeBuilder(final String securitySchemeName,
            final JsonObjectBuilder wrappedObjectBuilder) {
        super(securitySchemeName, wrappedObjectBuilder, MutableApiKeySecuritySchemeBuilder.class);
    }

    @Override
    SecuritySchemeScheme getSecuritySchemeScheme() {
        return SecuritySchemeScheme.APIKEY;
    }

    @Override
    public ApiKeySecurityScheme.Builder setIn(@Nullable final String in) {
        putValue(ApiKeySecurityScheme.JsonFields.IN, in);
        return myself;
    }

    @Override
    public ApiKeySecurityScheme.Builder setName(@Nullable final String name) {
        putValue(ApiKeySecurityScheme.JsonFields.NAME, name);
        return myself;
    }

    @Override
    public ApiKeySecurityScheme build() {
        return new ImmutableApiKeySecurityScheme(securitySchemeName, wrappedObjectBuilder.build());
    }
}
