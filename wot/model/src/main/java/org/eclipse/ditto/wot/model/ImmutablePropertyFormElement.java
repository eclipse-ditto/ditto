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

import java.util.Arrays;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;

/**
 * Immutable implementation of {@link PropertyFormElement}.
 */
@Immutable
final class ImmutablePropertyFormElement extends AbstractFormElement<PropertyFormElement>
        implements PropertyFormElement {

    private static final PropertyFormElementOp<SinglePropertyFormElementOp> OP_DEFAULT = MultiplePropertyFormElementOp.of(
            Arrays.asList(SinglePropertyFormElementOp.READPROPERTY, SinglePropertyFormElementOp.WRITEPROPERTY));

    ImmutablePropertyFormElement(final JsonObject wrappedObject) {
        super(wrappedObject);
    }

    @Override
    @SuppressWarnings("unchecked")
    public PropertyFormElementOp<SinglePropertyFormElementOp> getOp() {
        return Optional.ofNullable(TdHelpers.getValueIgnoringWrongType(wrappedObject, JsonFields.OP_MULTIPLE)
                        .map(MultiplePropertyFormElementOp::fromJson)
                        .map(PropertyFormElementOp.class::cast)
                        .orElseGet(() -> wrappedObject.getValue(JsonFields.OP)
                                .flatMap(SinglePropertyFormElementOp::forName)
                                .orElse(null))
                )
                .orElse(OP_DEFAULT);
    }

    @Override
    protected PropertyFormElement createInstance(final JsonObject newWrapped) {
        return new ImmutablePropertyFormElement(newWrapped);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImmutablePropertyFormElement;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }
}
