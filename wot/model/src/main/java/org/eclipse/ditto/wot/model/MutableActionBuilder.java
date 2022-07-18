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
 * Mutable builder for {@link Action}s.
 */
final class MutableActionBuilder
        extends AbstractInteractionBuilder<Action.Builder, Action, ActionFormElement, ActionForms>
        implements Action.Builder {

    private final String actionName;

    MutableActionBuilder(final String actionName, final JsonObjectBuilder wrappedObjectBuilder) {
        super(wrappedObjectBuilder, MutableActionBuilder.class);
        this.actionName = actionName;
    }

    @Override
    public Action.Builder setInput(@Nullable final SingleDataSchema input) {
        if (input != null) {
            putValue(Action.JsonFields.INPUT, input.toJson());
        } else {
            remove(Action.JsonFields.INPUT);
        }
        return myself;
    }

    @Override
    public Action.Builder setOutput(@Nullable final SingleDataSchema output) {
        if (output != null) {
            putValue(Action.JsonFields.OUTPUT, output.toJson());
        } else {
            remove(Action.JsonFields.OUTPUT);
        }
        return myself;
    }

    @Override
    public Action.Builder setSafe(@Nullable final Boolean safe) {
        if (safe != null) {
            putValue(Action.JsonFields.SAFE, safe);
        } else {
            remove(Action.JsonFields.SAFE);
        }
        return myself;
    }

    @Override
    public Action.Builder setIdempotent(@Nullable final Boolean idempotent) {
        if (idempotent != null) {
            putValue(Action.JsonFields.IDEMPOTENT, idempotent);
        } else {
            remove(Action.JsonFields.IDEMPOTENT);
        }
        return myself;
    }

    @Override
    public Action.Builder setSynchronous(@Nullable final Boolean synchronous) {
        if (synchronous != null) {
            putValue(Action.JsonFields.SYNCHRONOUS, synchronous);
        } else {
            remove(Action.JsonFields.SYNCHRONOUS);
        }
        return myself;
    }

    @Override
    public Action build() {
        return new ImmutableAction(actionName, wrappedObjectBuilder.build());
    }
}
