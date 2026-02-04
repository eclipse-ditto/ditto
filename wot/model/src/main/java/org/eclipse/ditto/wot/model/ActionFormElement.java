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
 * An ActionFormElement is a FormElement defined in {@link Action}s.
 *
 * @since 2.4.0
 */
public interface ActionFormElement extends FormElement<ActionFormElement> {

    /**
     * Creates a new ActionFormElement from the specified JSON object.
     *
     * @param jsonObject the JSON object representing the form element.
     * @return the ActionFormElement.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static ActionFormElement fromJson(final JsonObject jsonObject) {
        return new ImmutableActionFormElement(jsonObject);
    }

    /**
     * Creates a new builder for building an ActionFormElement.
     *
     * @return the builder.
     */
    static ActionFormElement.Builder newBuilder() {
        return ActionFormElement.Builder.newBuilder();
    }

    /**
     * Creates a new builder for building an ActionFormElement, initialized with the values from the specified
     * JSON object.
     *
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static ActionFormElement.Builder newBuilder(final JsonObject jsonObject) {
        return ActionFormElement.Builder.newBuilder(jsonObject);
    }

    @Override
    default ActionFormElement.Builder toBuilder() {
        return ActionFormElement.Builder.newBuilder(toJson());
    }

    /**
     * Returns the operation type(s) this form element supports for action affordances.
     *
     * @return the action operation type(s).
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#form">WoT TD Form (op)</a>
     */
    ActionFormElementOp<SingleActionFormElementOp> getOp();

    /**
     * A mutable builder with a fluent API for building an {@link ActionFormElement}.
     */
    interface Builder extends FormElement.Builder<Builder, ActionFormElement> {

        /**
         * Creates a new builder for building an ActionFormElement.
         *
         * @return the builder.
         */
        static Builder newBuilder() {
            return new MutableActionFormElementBuilder(JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building an ActionFormElement, initialized with the values from the
         * specified JSON object.
         *
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableActionFormElementBuilder(jsonObject.toBuilder());
        }

        /**
         * Sets the operation type(s) for this form element.
         *
         * @param op the operation type(s), or {@code null} to remove.
         * @return this builder.
         */
        Builder setOp(@Nullable ActionFormElementOp<SingleActionFormElementOp> op);

    }

}
