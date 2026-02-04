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

    /**
     * Creates a new EventFormElement from the specified JSON object.
     *
     * @param jsonObject the JSON object representing the form element.
     * @return the EventFormElement.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static EventFormElement fromJson(final JsonObject jsonObject) {
        return new ImmutableEventFormElement(jsonObject);
    }

    /**
     * Creates a new builder for building an EventFormElement.
     *
     * @return the builder.
     */
    static EventFormElement.Builder newBuilder() {
        return EventFormElement.Builder.newBuilder();
    }

    /**
     * Creates a new builder for building an EventFormElement, initialized with the values from the specified
     * JSON object.
     *
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static EventFormElement.Builder newBuilder(final JsonObject jsonObject) {
        return EventFormElement.Builder.newBuilder(jsonObject);
    }

    @Override
    default EventFormElement.Builder toBuilder() {
        return EventFormElement.Builder.newBuilder(toJson());
    }

    /**
     * Returns the operation type(s) this form element supports for event affordances.
     *
     * @return the event operation type(s).
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#form">WoT TD Form (op)</a>
     */
    EventFormElementOp<SingleEventFormElementOp> getOp();

    /**
     * A mutable builder with a fluent API for building an {@link EventFormElement}.
     */
    interface Builder extends FormElement.Builder<Builder, EventFormElement> {

        /**
         * Creates a new builder for building an EventFormElement.
         *
         * @return the builder.
         */
        static Builder newBuilder() {
            return new MutableEventFormElementBuilder(JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building an EventFormElement, initialized with the values from the
         * specified JSON object.
         *
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableEventFormElementBuilder(jsonObject.toBuilder());
        }

        /**
         * Sets the operation type(s) for this form element.
         *
         * @param op the operation type(s), or {@code null} to remove.
         * @return this builder.
         */
        Builder setOp(@Nullable EventFormElementOp<?> op);

    }
}
