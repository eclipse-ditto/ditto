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
 * An PropertyFormElement is a FormElement defined in {@link Property}s.
 *
 * @since 2.4.0
 */
public interface PropertyFormElement extends FormElement<PropertyFormElement> {

    /**
     * Creates a new PropertyFormElement from the specified JSON object.
     *
     * @param jsonObject the JSON object representing the form element.
     * @return the PropertyFormElement.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static PropertyFormElement fromJson(final JsonObject jsonObject) {
        return new ImmutablePropertyFormElement(jsonObject);
    }

    /**
     * Creates a new builder for building a PropertyFormElement.
     *
     * @return the builder.
     */
    static PropertyFormElement.Builder newBuilder() {
        return PropertyFormElement.Builder.newBuilder();
    }

    /**
     * Creates a new builder for building a PropertyFormElement, initialized with the values from the specified
     * JSON object.
     *
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static PropertyFormElement.Builder newBuilder(final JsonObject jsonObject) {
        return PropertyFormElement.Builder.newBuilder(jsonObject);
    }

    @Override
    default PropertyFormElement.Builder toBuilder() {
        return PropertyFormElement.Builder.newBuilder(toJson());
    }

    /**
     * Returns the operation type(s) this form element supports for property affordances.
     *
     * @return the property operation type(s).
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#form">WoT TD Form (op)</a>
     */
    PropertyFormElementOp<SinglePropertyFormElementOp> getOp();

    /**
     * A mutable builder with a fluent API for building a {@link PropertyFormElement}.
     */
    interface Builder extends FormElement.Builder<Builder, PropertyFormElement> {

        /**
         * Creates a new builder for building a PropertyFormElement.
         *
         * @return the builder.
         */
        static Builder newBuilder() {
            return new MutablePropertyFormElementBuilder(JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building a PropertyFormElement, initialized with the values from the
         * specified JSON object.
         *
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutablePropertyFormElementBuilder(jsonObject.toBuilder());
        }

        /**
         * Sets the operation type(s) for this form element.
         *
         * @param op the operation type(s), or {@code null} to remove.
         * @return this builder.
         */
        Builder setOp(@Nullable PropertyFormElementOp<SinglePropertyFormElementOp> op);

    }

}
