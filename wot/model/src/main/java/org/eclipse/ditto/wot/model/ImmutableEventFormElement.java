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
 * Immutable implementation of {@link EventFormElement}.
 */
@Immutable
final class ImmutableEventFormElement extends AbstractFormElement<EventFormElement> implements EventFormElement {

    private static final EventFormElementOp<SingleEventFormElementOp> OP_DEFAULT = MultipleEventFormElementOp.of(
            Arrays.asList(SingleEventFormElementOp.SUBSCRIBEEVENT, SingleEventFormElementOp.UNSUBSCRIBEEVENT));

    ImmutableEventFormElement(final JsonObject wrappedObject) {
        super(wrappedObject);
    }

    @Override
    @SuppressWarnings("unchecked")
    public EventFormElementOp<SingleEventFormElementOp> getOp() {
        return Optional.ofNullable(TdHelpers.getValueIgnoringWrongType(wrappedObject, JsonFields.OP_MULTIPLE)
                        .map(MultipleEventFormElementOp::fromJson)
                        .map(EventFormElementOp.class::cast)
                        .orElseGet(() -> wrappedObject.getValue(JsonFields.OP)
                                .flatMap(SingleEventFormElementOp::forName)
                                .orElse(null))
                )
                .orElse(OP_DEFAULT);
    }

    @Override
    protected EventFormElement createInstance(final JsonObject newWrapped) {
        return new ImmutableEventFormElement(newWrapped);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImmutableEventFormElement;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }
}
