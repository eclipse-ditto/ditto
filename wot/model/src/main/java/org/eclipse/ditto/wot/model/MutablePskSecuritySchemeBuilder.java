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
 * Mutable builder for {@link PskSecurityScheme}s.
 */
final class MutablePskSecuritySchemeBuilder
        extends AbstractSecuritySchemeBuilder<PskSecurityScheme.Builder, PskSecurityScheme>
        implements PskSecurityScheme.Builder {

    MutablePskSecuritySchemeBuilder(final String securitySchemeName,
            final JsonObjectBuilder wrappedObjectBuilder) {
        super(securitySchemeName, wrappedObjectBuilder, MutablePskSecuritySchemeBuilder.class);
    }

    @Override
    SecuritySchemeScheme getSecuritySchemeScheme() {
        return SecuritySchemeScheme.PSK;
    }


    @Override
    public PskSecurityScheme.Builder setIdentity(@Nullable final String identity) {
        putValue(PskSecurityScheme.JsonFields.IDENTITY, identity);
        return myself;
    }

    @Override
    public PskSecurityScheme build() {
        return new ImmutablePskSecurityScheme(securitySchemeName, wrappedObjectBuilder.build());
    }
}
