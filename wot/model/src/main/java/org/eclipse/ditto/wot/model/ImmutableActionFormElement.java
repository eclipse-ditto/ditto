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

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;

/**
 * Immutable implementation of {@link ActionFormElement}.
 */
@Immutable
final class ImmutableActionFormElement extends AbstractFormElement<ActionFormElement> implements ActionFormElement {

    private static final ActionFormElementOp<SingleActionFormElementOp> OP_DEFAULT = SingleActionFormElementOp.INVOKEACTION;

    ImmutableActionFormElement(final JsonObject wrappedObject) {
        super(wrappedObject);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ActionFormElementOp<SingleActionFormElementOp> getOp() {
        return Optional.ofNullable(TdHelpers.getValueIgnoringWrongType(wrappedObject, JsonFields.OP_MULTIPLE)
                        .map(MultipleActionFormElementOp::fromJson)
                        .map(ActionFormElementOp.class::cast)
                        .orElseGet(() -> wrappedObject.getValue(JsonFields.OP)
                                .flatMap(SingleActionFormElementOp::forName)
                                .orElse(null))
                )
                .orElse(OP_DEFAULT);
    }

    @Override
    protected ActionFormElement createInstance(final JsonObject newWrapped) {
        return new ImmutableActionFormElement(newWrapped);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImmutableActionFormElement;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }
}
