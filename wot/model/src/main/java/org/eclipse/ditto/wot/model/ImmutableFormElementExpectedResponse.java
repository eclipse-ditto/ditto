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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;

/**
 * Immutable implementation of {@link FormElementExpectedResponse}.
 */
@Immutable
final class ImmutableFormElementExpectedResponse implements FormElementExpectedResponse {

    private final JsonObject wrappedObject;

    ImmutableFormElementExpectedResponse(final JsonObject wrappedObject) {
        this.wrappedObject = wrappedObject;
    }

    @Override
    public JsonObject toJson() {
        return wrappedObject;
    }

    @Override
    public Optional<String> getContentType() {
        return wrappedObject.getValue(JsonFields.CONTENT_TYPE);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableFormElementExpectedResponse that = (ImmutableFormElementExpectedResponse) o;
        return Objects.equals(wrappedObject, that.wrappedObject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(wrappedObject);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "wrappedObject=" + wrappedObject +
                "]";
    }
}
