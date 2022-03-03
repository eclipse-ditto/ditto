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
 * Mutable builder for {@link BearerSecurityScheme}s.
 */
final class MutableBearerSecuritySchemeBuilder
        extends AbstractSecuritySchemeBuilder<BearerSecurityScheme.Builder, BearerSecurityScheme>
        implements BearerSecurityScheme.Builder {

    MutableBearerSecuritySchemeBuilder(final String securitySchemeName,
            final JsonObjectBuilder wrappedObjectBuilder) {
        super(securitySchemeName, wrappedObjectBuilder, MutableBearerSecuritySchemeBuilder.class);
    }

    @Override
    SecuritySchemeScheme getSecuritySchemeScheme() {
        return SecuritySchemeScheme.BEARER;
    }

    @Override
    public BearerSecurityScheme.Builder setAuthorization(@Nullable final IRI authorization) {
        if (authorization != null) {
            putValue(BearerSecurityScheme.JsonFields.AUTHORIZATION, authorization.toString());
        } else {
            remove(BearerSecurityScheme.JsonFields.AUTHORIZATION);
        }
        return myself;
    }

    @Override
    public BearerSecurityScheme.Builder setAlg(@Nullable final String alg) {
        putValue(BearerSecurityScheme.JsonFields.ALG, alg);
        return myself;
    }

    @Override
    public BearerSecurityScheme.Builder setFormat(@Nullable final String format) {
        putValue(BearerSecurityScheme.JsonFields.FORMAT, format);
        return myself;
    }

    @Override
    public BearerSecurityScheme.Builder setIn(@Nullable final String in) {
        putValue(BearerSecurityScheme.JsonFields.IN, in);
        return myself;
    }

    @Override
    public BearerSecurityScheme.Builder setName(@Nullable final String name) {
        putValue(BearerSecurityScheme.JsonFields.NAME, name);
        return myself;
    }

    @Override
    public BearerSecurityScheme build() {
        return new ImmutableBearerSecurityScheme(securitySchemeName, wrappedObjectBuilder.build());
    }
}
