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
 * Mutable builder for {@link NoSecurityScheme}s.
 */
final class MutableNoSecuritySchemeBuilder
        extends AbstractSecuritySchemeBuilder<NoSecurityScheme.Builder, NoSecurityScheme>
        implements NoSecurityScheme.Builder {

    MutableNoSecuritySchemeBuilder(final String securitySchemeName,
            final JsonObjectBuilder wrappedObjectBuilder) {
        super(securitySchemeName, wrappedObjectBuilder, MutableNoSecuritySchemeBuilder.class);
    }

    @Override
    SecuritySchemeScheme getSecuritySchemeScheme() {
        return SecuritySchemeScheme.NOSEC;
    }

    @Override
    public NoSecurityScheme build() {
        return new ImmutableNoSecurityScheme(securitySchemeName, wrappedObjectBuilder.build());
    }
}
