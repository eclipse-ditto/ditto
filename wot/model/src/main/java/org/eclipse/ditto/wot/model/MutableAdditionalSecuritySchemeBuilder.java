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

import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Mutable builder for {@link AdditionalSecurityScheme}s.
 */
final class MutableAdditionalSecuritySchemeBuilder
        extends AbstractSecuritySchemeBuilder<AdditionalSecurityScheme.Builder, AdditionalSecurityScheme>
        implements AdditionalSecurityScheme.Builder {

    private final SecuritySchemeScheme contextExtensionScopedScheme;

    MutableAdditionalSecuritySchemeBuilder(final String securitySchemeName,
            final SecuritySchemeScheme contextExtensionScopedScheme,
            final JsonObjectBuilder wrappedObjectBuilder) {
        super(securitySchemeName, wrappedObjectBuilder, MutableAdditionalSecuritySchemeBuilder.class);
        this.contextExtensionScopedScheme = contextExtensionScopedScheme;
    }

    @Override
    SecuritySchemeScheme getSecuritySchemeScheme() {
        return contextExtensionScopedScheme;
    }

    @Override
    public AdditionalSecurityScheme build() {
        return new ImmutableAdditionalSecurityScheme(securitySchemeName, wrappedObjectBuilder.build());
    }
}
