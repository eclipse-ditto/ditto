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
 * Mutable builder for {@link BasicSecurityScheme}s.
 */
final class MutableBasicSecuritySchemeBuilder
        extends AbstractSecuritySchemeBuilder<BasicSecurityScheme.Builder, BasicSecurityScheme>
        implements BasicSecurityScheme.Builder {

    MutableBasicSecuritySchemeBuilder(final String securitySchemeName,
            final JsonObjectBuilder wrappedObjectBuilder) {
        super(securitySchemeName, wrappedObjectBuilder, MutableBasicSecuritySchemeBuilder.class);
    }

    @Override
    SecuritySchemeScheme getSecuritySchemeScheme() {
        return SecuritySchemeScheme.BASIC;
    }

    @Override
    public BasicSecurityScheme.Builder setIn(@Nullable final String in) {
        putValue(BasicSecurityScheme.JsonFields.IN, in);
        return myself;
    }

    @Override
    public BasicSecurityScheme.Builder setName(@Nullable final String name) {
        putValue(BasicSecurityScheme.JsonFields.NAME, name);
        return myself;
    }

    @Override
    public BasicSecurityScheme build() {
        return new ImmutableBasicSecurityScheme(securitySchemeName, wrappedObjectBuilder.build());
    }
}
