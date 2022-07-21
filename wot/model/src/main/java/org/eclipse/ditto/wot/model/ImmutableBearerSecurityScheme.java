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
 * Immutable implementation of {@link BearerSecurityScheme}.
 */
@Immutable
final class ImmutableBearerSecurityScheme extends AbstractSecurityScheme implements BearerSecurityScheme {

    ImmutableBearerSecurityScheme(final String securitySchemeName, final JsonObject wrappedObject) {
        super(securitySchemeName, wrappedObject);
    }

    @Override
    public Optional<IRI> getAuthorization() {
        return wrappedObject.getValue(JsonFields.AUTHORIZATION)
                .map(IRI::of);
    }

    @Override
    public Optional<String> getAlg() {
        return wrappedObject.getValue(JsonFields.ALG);
    }

    @Override
    public Optional<String> getFormat() {
        return wrappedObject.getValue(JsonFields.FORMAT);
    }

    @Override
    public Optional<SecuritySchemeIn> getIn() {
        return wrappedObject.getValue(JsonFields.IN)
                .flatMap(SecuritySchemeIn::forName);
    }

    @Override
    public Optional<String> getName() {
        return wrappedObject.getValue(JsonFields.NAME);
    }

    @Override
    protected SecurityScheme createInstance(final JsonObject newWrapped) {
        return new ImmutableBearerSecurityScheme(getSecuritySchemeName(), newWrapped);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImmutableBearerSecurityScheme;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }
}
