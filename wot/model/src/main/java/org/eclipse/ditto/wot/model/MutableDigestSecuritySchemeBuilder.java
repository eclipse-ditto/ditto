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
 * Mutable builder for {@link DigestSecurityScheme}s.
 */
final class MutableDigestSecuritySchemeBuilder
        extends AbstractSecuritySchemeBuilder<DigestSecurityScheme.Builder, DigestSecurityScheme>
        implements DigestSecurityScheme.Builder {

    MutableDigestSecuritySchemeBuilder(final String securitySchemeName,
            final JsonObjectBuilder wrappedObjectBuilder) {
        super(securitySchemeName, wrappedObjectBuilder, MutableDigestSecuritySchemeBuilder.class);
    }

    @Override
    SecuritySchemeScheme getSecuritySchemeScheme() {
        return SecuritySchemeScheme.DIGEST;
    }

    @Override
    public DigestSecurityScheme.Builder setQop(@Nullable final DigestSecurityScheme.Qop qop) {
        if (qop != null) {
            putValue(DigestSecurityScheme.JsonFields.QOP, qop.getName());
        } else {
            remove(DigestSecurityScheme.JsonFields.QOP);
        }
        return myself;
    }

    @Override
    public DigestSecurityScheme.Builder setIn(@Nullable final String in) {
        putValue(DigestSecurityScheme.JsonFields.IN, in);
        return myself;
    }

    @Override
    public DigestSecurityScheme.Builder setName(@Nullable final String name) {
        putValue(DigestSecurityScheme.JsonFields.NAME, name);
        return myself;
    }

    @Override
    public DigestSecurityScheme build() {
        return new ImmutableDigestSecurityScheme(securitySchemeName, wrappedObjectBuilder.build());
    }
}
