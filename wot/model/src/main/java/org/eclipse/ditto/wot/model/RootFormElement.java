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
 * A RootFormElement is a FormElement defined on TD root (thing) level.
 * "Thing-level forms are used to describe endpoints for a group of interaction affordances."
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#form-top-level">WoT TD Top level forms</a>
 * @since 2.4.0
 */
public interface RootFormElement extends FormElement<RootFormElement> {

    /**
     * Creates a new RootFormElement from the specified JSON object.
     *
     * @param jsonObject the JSON object representing the form element.
     * @return the RootFormElement.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static RootFormElement fromJson(final JsonObject jsonObject) {
        return new ImmutableRootFormElement(jsonObject);
    }

    /**
     * Creates a new builder for building a RootFormElement.
     *
     * @return the builder.
     */
    static RootFormElement.Builder newBuilder() {
        return RootFormElement.Builder.newBuilder();
    }

    /**
     * Creates a new builder for building a RootFormElement, initialized with the values from the specified JSON object.
     *
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static RootFormElement.Builder newBuilder(final JsonObject jsonObject) {
        return RootFormElement.Builder.newBuilder(jsonObject);
    }

    @Override
    default RootFormElement.Builder toBuilder() {
        return RootFormElement.Builder.newBuilder(toJson());
    }

    /**
     * Returns the operation type(s) this form element supports for root-level operations.
     *
     * @return the root form element operation type(s).
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#form-top-level">WoT TD Top level forms (op)</a>
     */
    RootFormElementOp<SingleRootFormElementOp> getOp();

    /**
     * A mutable builder with a fluent API for building a {@link RootFormElement}.
     */
    interface Builder extends FormElement.Builder<Builder, RootFormElement> {

        /**
         * Creates a new builder for building a RootFormElement.
         *
         * @return the builder.
         */
        static Builder newBuilder() {
            return new MutableRootFormElementBuilder(JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building a RootFormElement, initialized with the values from the
         * specified JSON object.
         *
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableRootFormElementBuilder(jsonObject.toBuilder());
        }

        /**
         * Sets the operation type(s) for this form element.
         *
         * @param op the operation type(s), or {@code null} to remove.
         * @return this builder.
         */
        Builder setOp(@Nullable RootFormElementOp<SingleRootFormElementOp> op);

    }
}
