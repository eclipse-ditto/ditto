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

import org.eclipse.ditto.json.JsonObject;

/**
 * An EventFormElement is a FormElement defined in {@link Event}s.
 *
 * @since 2.4.0
 */
public interface EventFormElement extends FormElement<EventFormElement> {

    static EventFormElement fromJson(final JsonObject jsonObject) {
        return new ImmutableEventFormElement(jsonObject);
    }

    static EventFormElement.Builder newBuilder() {
        return EventFormElement.Builder.newBuilder();
    }

    static EventFormElement.Builder newBuilder(final JsonObject jsonObject) {
        return EventFormElement.Builder.newBuilder(jsonObject);
    }

    @Override
    default EventFormElement.Builder toBuilder() {
        return EventFormElement.Builder.newBuilder(toJson());
    }

    EventFormElementOp<SingleEventFormElementOp> getOp();
    
    interface Builder extends FormElement.Builder<Builder, EventFormElement> {

        static Builder newBuilder() {
            return new MutableEventFormElementBuilder(JsonObject.newBuilder());
        }

        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableEventFormElementBuilder(jsonObject.toBuilder());
        }

        Builder setOp(@Nullable EventFormElementOp<?> op);

    }
}
