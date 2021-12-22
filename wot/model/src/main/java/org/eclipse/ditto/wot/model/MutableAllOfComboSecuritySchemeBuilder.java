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

import java.util.Collection;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Mutable builder for {@link AllOfComboSecurityScheme}s.
 */
final class MutableAllOfComboSecuritySchemeBuilder
        extends AbstractSecuritySchemeBuilder<AllOfComboSecurityScheme.Builder, AllOfComboSecurityScheme>
        implements AllOfComboSecurityScheme.Builder {

    MutableAllOfComboSecuritySchemeBuilder(final String securitySchemeName,
            final JsonObjectBuilder wrappedObjectBuilder) {
        super(securitySchemeName, wrappedObjectBuilder, MutableAllOfComboSecuritySchemeBuilder.class);
    }

    @Override
    SecuritySchemeScheme getSecuritySchemeScheme() {
        return SecuritySchemeScheme.COMBO;
    }

    @Override
    public AllOfComboSecurityScheme.Builder setAllOf(@Nullable final Collection<SecurityScheme> securitySchemes) {
        if (securitySchemes != null) {
            putValue(AllOfComboSecurityScheme.JsonFields.ALL_OF, securitySchemes.stream()
                    .map(SecurityScheme::toJson)
                    .collect(JsonCollectors.valuesToArray()));
        } else {
            remove(AllOfComboSecurityScheme.JsonFields.ALL_OF);
        }
        return myself;
    }

    @Override
    public AllOfComboSecurityScheme build() {
        return new ImmutableAllOfComboSecurityScheme(securitySchemeName, wrappedObjectBuilder.build());
    }
}
