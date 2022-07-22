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
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#actionaffordance">WoT TD ActionAffordance</a>
 * @since 2.4.0
 */
public interface Action extends Interaction<Action, ActionFormElement, ActionForms> {

    static Action fromJson(final CharSequence actionName, final JsonObject jsonObject) {
        return new ImmutableAction(checkNotNull(actionName, "actionName").toString(), jsonObject);
    }

    static Action.Builder newBuilder(final CharSequence actionName) {
        return Action.Builder.newBuilder(actionName);
    }

    static Action.Builder newBuilder(final CharSequence actionName, final JsonObject jsonObject) {
        return Action.Builder.newBuilder(actionName, jsonObject);
    }

    @Override
    default Action.Builder toBuilder() {
        return Builder.newBuilder(getActionName(), toJson());
    }

    String getActionName();

    Optional<SingleDataSchema> getInput();

    Optional<SingleDataSchema> getOutput();

    boolean isSafe();

    boolean isIdempotent();

    Optional<Boolean> isSynchronous();

    interface Builder extends Interaction.Builder<Builder, Action, ActionFormElement, ActionForms> {

        static Builder newBuilder(final CharSequence actionName) {
            return new MutableActionBuilder(checkNotNull(actionName, "actionName").toString(),
                    JsonObject.newBuilder());
        }

        static Builder newBuilder(final CharSequence actionName, final JsonObject jsonObject) {
            return new MutableActionBuilder(checkNotNull(actionName, "actionName").toString(), jsonObject.toBuilder());
        }

        Builder setInput(@Nullable SingleDataSchema input);

        Builder setOutput(@Nullable SingleDataSchema output);

        Builder setSafe(@Nullable Boolean safe);

        Builder setIdempotent(@Nullable Boolean idempotent);

        Builder setSynchronous(@Nullable Boolean synchronous);
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of an Action.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<JsonObject> INPUT = JsonFactory.newJsonObjectFieldDefinition(
                "input");

        public static final JsonFieldDefinition<JsonObject> OUTPUT = JsonFactory.newJsonObjectFieldDefinition(
                "output");

        public static final JsonFieldDefinition<Boolean> SAFE = JsonFactory.newBooleanFieldDefinition(
                "safe");

        public static final JsonFieldDefinition<Boolean> IDEMPOTENT = JsonFactory.newBooleanFieldDefinition(
                "idempotent");

        public static final JsonFieldDefinition<Boolean> SYNCHRONOUS = JsonFactory.newBooleanFieldDefinition(
                "synchronous");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
