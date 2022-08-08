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
 * Immutable implementation of {@link FormElementExpectedResponse}.
 */
@Immutable
final class ImmutableFormElementExpectedResponse extends AbstractTypedJsonObject<FormElementExpectedResponse>
        implements FormElementExpectedResponse {

    ImmutableFormElementExpectedResponse(final JsonObject wrappedObject) {
        super(wrappedObject);
    }

    @Override
    protected FormElementExpectedResponse createInstance(final JsonObject newWrapped) {
        return new ImmutableFormElementExpectedResponse(newWrapped);
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
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImmutableFormElementExpectedResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
