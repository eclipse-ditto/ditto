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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * An Action is an {@link Interaction} describing a function which can be invoked on a Thing.
 * <p>
 * Actions allow Consumers to invoke functions on a Thing, potentially passing input data and receiving output data
 * as a result. Unlike Properties, Actions may take time to complete and may have side effects.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#actionaffordance">WoT TD ActionAffordance</a>
 * @since 2.4.0
 */
public interface Action extends Interaction<Action, ActionFormElement, ActionForms> {

    /**
     * Creates a new Action from the specified JSON object.
     *
     * @param actionName the name of the action (the key in the actions map).
     * @param jsonObject the JSON object representing the action affordance.
     * @return the Action.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Action fromJson(final CharSequence actionName, final JsonObject jsonObject) {
        return new ImmutableAction(checkNotNull(actionName, "actionName").toString(), jsonObject);
    }

    /**
     * Creates a new builder for building an Action.
     *
     * @param actionName the name of the action.
     * @return the builder.
     * @throws NullPointerException if {@code actionName} is {@code null}.
     */
    static Action.Builder newBuilder(final CharSequence actionName) {
        return Action.Builder.newBuilder(actionName);
    }

    /**
     * Creates a new builder for building an Action, initialized with the values from the specified JSON object.
     *
     * @param actionName the name of the action.
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Action.Builder newBuilder(final CharSequence actionName, final JsonObject jsonObject) {
        return Action.Builder.newBuilder(actionName, jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for building an Action, initialized with the values
     * of this instance.
     *
     * @return the builder.
     */
    @Override
    default Action.Builder toBuilder() {
        return Builder.newBuilder(getActionName(), toJson());
    }

    /**
     * Returns the name of this action as defined in the Thing Description's actions map.
     *
     * @return the action name.
     */
    String getActionName();

    /**
     * Returns the optional data schema describing the input data accepted by this action.
     *
     * @return the optional input schema.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#actionaffordance">WoT TD ActionAffordance (input)</a>
     */
    Optional<SingleDataSchema> getInput();

    /**
     * Returns the optional data schema describing the output data returned by this action.
     *
     * @return the optional output schema.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#actionaffordance">WoT TD ActionAffordance (output)</a>
     */
    Optional<SingleDataSchema> getOutput();

    /**
     * Returns whether this action is safe, meaning it does not change the state of the Thing.
     * <p>
     * Safe actions can be called without risk of unintended side effects. The default is {@code false}.
     * </p>
     *
     * @return {@code true} if the action is safe, {@code false} otherwise.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#actionaffordance">WoT TD ActionAffordance (safe)</a>
     */
    boolean isSafe();

    /**
     * Returns whether this action is idempotent, meaning repeated invocations with the same input
     * produce the same result without additional side effects.
     * <p>
     * The default is {@code false}.
     * </p>
     *
     * @return {@code true} if the action is idempotent, {@code false} otherwise.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#actionaffordance">WoT TD ActionAffordance (idempotent)</a>
     */
    boolean isIdempotent();

    /**
     * Returns the optional indication of whether the action is synchronous.
     * <p>
     * A synchronous action completes within a single request-response interaction,
     * while an asynchronous action may take longer and require polling for completion.
     * </p>
     *
     * @return the optional synchronous indicator.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#actionaffordance">WoT TD ActionAffordance (synchronous)</a>
     */
    Optional<Boolean> isSynchronous();

    /**
     * A mutable builder with a fluent API for building an {@link Action}.
     */
    interface Builder extends Interaction.Builder<Builder, Action, ActionFormElement, ActionForms> {

        /**
         * Creates a new builder for building an Action.
         *
         * @param actionName the name of the action.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence actionName) {
            return new MutableActionBuilder(checkNotNull(actionName, "actionName").toString(),
                    JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building an Action, initialized with the values from the specified JSON object.
         *
         * @param actionName the name of the action.
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence actionName, final JsonObject jsonObject) {
            return new MutableActionBuilder(checkNotNull(actionName, "actionName").toString(), jsonObject.toBuilder());
        }

        /**
         * Sets the input data schema for this action.
         *
         * @param input the input schema, or {@code null} to remove.
         * @return this builder.
         * @see <a href="https://www.w3.org/TR/wot-thing-description11/#actionaffordance">WoT TD ActionAffordance (input)</a>
         */
        Builder setInput(@Nullable SingleDataSchema input);

        /**
         * Sets the output data schema for this action.
         *
         * @param output the output schema, or {@code null} to remove.
         * @return this builder.
         * @see <a href="https://www.w3.org/TR/wot-thing-description11/#actionaffordance">WoT TD ActionAffordance (output)</a>
         */
        Builder setOutput(@Nullable SingleDataSchema output);

        /**
         * Sets whether this action is safe.
         *
         * @param safe whether the action is safe, or {@code null} to remove.
         * @return this builder.
         * @see <a href="https://www.w3.org/TR/wot-thing-description11/#actionaffordance">WoT TD ActionAffordance (safe)</a>
         */
        Builder setSafe(@Nullable Boolean safe);

        /**
         * Sets whether this action is idempotent.
         *
         * @param idempotent whether the action is idempotent, or {@code null} to remove.
         * @return this builder.
         * @see <a href="https://www.w3.org/TR/wot-thing-description11/#actionaffordance">WoT TD ActionAffordance (idempotent)</a>
         */
        Builder setIdempotent(@Nullable Boolean idempotent);

        /**
         * Sets whether this action is synchronous.
         *
         * @param synchronous whether the action is synchronous, or {@code null} to remove.
         * @return this builder.
         * @see <a href="https://www.w3.org/TR/wot-thing-description11/#actionaffordance">WoT TD ActionAffordance (synchronous)</a>
         */
        Builder setSynchronous(@Nullable Boolean synchronous);
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of an Action.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the input data schema.
         */
        public static final JsonFieldDefinition<JsonObject> INPUT = JsonFactory.newJsonObjectFieldDefinition(
                "input");

        /**
         * JSON field definition for the output data schema.
         */
        public static final JsonFieldDefinition<JsonObject> OUTPUT = JsonFactory.newJsonObjectFieldDefinition(
                "output");

        /**
         * JSON field definition for the safe flag.
         */
        public static final JsonFieldDefinition<Boolean> SAFE = JsonFactory.newBooleanFieldDefinition(
                "safe");

        /**
         * JSON field definition for the idempotent flag.
         */
        public static final JsonFieldDefinition<Boolean> IDEMPOTENT = JsonFactory.newBooleanFieldDefinition(
                "idempotent");

        /**
         * JSON field definition for the synchronous flag.
         */
        public static final JsonFieldDefinition<Boolean> SYNCHRONOUS = JsonFactory.newBooleanFieldDefinition(
                "synchronous");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
