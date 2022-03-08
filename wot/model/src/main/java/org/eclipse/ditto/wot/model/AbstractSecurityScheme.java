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

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;

/**
 * Abstract implementation of {@link SecurityScheme}.
 */
abstract class AbstractSecurityScheme implements SecurityScheme {

    private final String securitySchemeName;
    protected final JsonObject wrappedObject;

    AbstractSecurityScheme(final String securitySchemeName, final JsonObject wrappedObject) {
        this.securitySchemeName = securitySchemeName;
        this.wrappedObject = wrappedObject;
    }

    @Override
    public String getSecuritySchemeName() {
        return securitySchemeName;
    }

    @Override
    public Optional<AtType> getAtType() {
        return Optional.ofNullable(
                TdHelpers.getValueIgnoringWrongType(wrappedObject, SecuritySchemeJsonFields.AT_TYPE_MULTIPLE)
                        .map(MultipleAtType::fromJson)
                        .map(AtType.class::cast)
                        .orElseGet(() -> wrappedObject.getValue(SecuritySchemeJsonFields.AT_TYPE)
                                .map(SingleAtType::of)
                                .orElse(null))
        );
    }

    @Override
    public Optional<Description> getDescription() {
        return wrappedObject.getValue(SecuritySchemeJsonFields.DESCRIPTION)
                .map(Description::of);
    }

    @Override
    public Optional<Descriptions> getDescriptions() {
        return wrappedObject.getValue(SecuritySchemeJsonFields.DESCRIPTIONS)
                .map(Descriptions::fromJson);
    }

    @Override
    public Optional<IRI> getProxy() {
        return wrappedObject.getValue(SecuritySchemeJsonFields.PROXY)
                .map(IRI::of);
    }

    @Override
    public SecuritySchemeScheme getScheme() {
        final String schemeStr = wrappedObject.getValueOrThrow(SecuritySchemeJsonFields.SCHEME);
        return SecuritySchemeScheme.forName(schemeStr)
                .orElseThrow(() -> new IllegalArgumentException("Unknown scheme: " + schemeStr));

    }

    @Override
    public JsonObject toJson() {
        return wrappedObject;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractSecurityScheme that = (AbstractSecurityScheme) o;
        return canEqual(that) && Objects.equals(securitySchemeName, that.securitySchemeName) &&
                Objects.equals(wrappedObject, that.wrappedObject);
    }

    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractSecurityScheme;
    }

    @Override
    public int hashCode() {
        return Objects.hash(securitySchemeName, wrappedObject);
    }

    @Override
    public String toString() {
        return "securitySchemeName=" + securitySchemeName +
                ", wrappedObject=" + wrappedObject;
    }

}
