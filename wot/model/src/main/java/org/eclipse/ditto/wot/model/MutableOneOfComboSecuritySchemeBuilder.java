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
 * Mutable builder for {@link OneOfComboSecurityScheme}s.
 */
final class MutableOneOfComboSecuritySchemeBuilder
        extends AbstractSecuritySchemeBuilder<OneOfComboSecurityScheme.Builder, OneOfComboSecurityScheme>
        implements OneOfComboSecurityScheme.Builder {

    MutableOneOfComboSecuritySchemeBuilder(final String securitySchemeName,
            final JsonObjectBuilder wrappedObjectBuilder) {
        super(securitySchemeName, wrappedObjectBuilder, MutableOneOfComboSecuritySchemeBuilder.class);
    }

    @Override
    SecuritySchemeScheme getSecuritySchemeScheme() {
        return SecuritySchemeScheme.COMBO;
    }

    @Override
    public OneOfComboSecurityScheme.Builder setOneOf(@Nullable final Collection<SecurityScheme> securitySchemes) {
        if (securitySchemes != null) {
            putValue(OneOfComboSecurityScheme.JsonFields.ONE_OF, securitySchemes.stream()
                    .map(SecurityScheme::toJson)
                    .collect(JsonCollectors.valuesToArray()));
        } else {
            remove(OneOfComboSecurityScheme.JsonFields.ONE_OF);
        }
        return myself;
    }

    @Override
    public OneOfComboSecurityScheme build() {
        return new ImmutableOneOfComboSecurityScheme(securitySchemeName, wrappedObjectBuilder.build());
    }
}
