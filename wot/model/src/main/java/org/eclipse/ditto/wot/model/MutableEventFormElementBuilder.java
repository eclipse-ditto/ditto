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
 * Mutable builder for {@link EventFormElement}s.
 */
final class MutableEventFormElementBuilder
        extends AbstractFormElementBuilder<EventFormElement.Builder, EventFormElement>
        implements EventFormElement.Builder {

    MutableEventFormElementBuilder(final JsonObjectBuilder wrappedObjectBuilder) {
        super(wrappedObjectBuilder, MutableEventFormElementBuilder.class);
    }

    @Override
    public EventFormElement.Builder setOp(@Nullable final EventFormElementOp<?> op) {
        if (op != null) {
            if (op instanceof MultipleEventFormElementOp) {
                putValue(FormElement.JsonFields.OP_MULTIPLE, ((MultipleEventFormElementOp) op).toJson());
            } else if (op instanceof SingleEventFormElementOp) {
                putValue(FormElement.JsonFields.OP, op.toString());
            } else {
                throw new IllegalArgumentException("Unsupported op: " + op.getClass().getSimpleName());
            }
        } else {
            remove(FormElement.JsonFields.OP);
        }
        return myself;
    }

    @Override
    public EventFormElement build() {
        return new ImmutableEventFormElement(wrappedObjectBuilder.build());
    }
}
