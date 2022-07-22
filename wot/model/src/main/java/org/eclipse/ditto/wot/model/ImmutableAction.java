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

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable implementation of {@link Action}.
 */
@Immutable
final class ImmutableAction extends AbstractInteraction<Action, ActionFormElement, ActionForms> implements Action {

    private static final boolean SAFE_DEFAULT = false;
    private static final boolean IDEMPOTENT_DEFAULT = false;

    private final String actionName;

    ImmutableAction(final String actionName, final JsonObject wrappedObject) {
        super(wrappedObject);
        this.actionName = actionName;
    }

    @Override
    public String getActionName() {
        return actionName;
    }

    @Override
    public Optional<SingleDataSchema> getInput() {
        return wrappedObject.getValue(JsonFields.INPUT)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(SingleDataSchema::fromJson);
    }

    @Override
    public Optional<SingleDataSchema> getOutput() {
        return wrappedObject.getValue(JsonFields.OUTPUT)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(SingleDataSchema::fromJson);
    }

    @Override
    public boolean isSafe() {
        return wrappedObject.getValue(JsonFields.SAFE).orElse(SAFE_DEFAULT);
    }

    @Override
    public boolean isIdempotent() {
        return wrappedObject.getValue(JsonFields.IDEMPOTENT).orElse(IDEMPOTENT_DEFAULT);
    }

    @Override
    public Optional<Boolean> isSynchronous() {
        return wrappedObject.getValue(JsonFields.SYNCHRONOUS);
    }

    @Override
    public Optional<ActionForms> getForms() {
        return wrappedObject.getValue(InteractionJsonFields.FORMS)
                .map(ActionForms::fromJson);
    }

    @Override
    protected Action createInstance(final JsonObject newWrapped) {
        return new ImmutableAction(actionName, newWrapped);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableAction that = (ImmutableAction) o;
        return super.equals(o) && Objects.equals(actionName, that.actionName);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImmutableAction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(actionName, super.hashCode());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "actionName=" + actionName +
                ", " + super.toString() +
                "]";
    }

}
