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
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;

/**
 * Immutable implementation of {@link RootFormElement}.
 */
@Immutable
final class ImmutableRootFormElement extends AbstractFormElement<RootFormElement> implements RootFormElement {

    ImmutableRootFormElement(final JsonObject wrappedObject) {
        super(wrappedObject);
    }

    @Override
    @SuppressWarnings("unchecked")
    public RootFormElementOp<SingleRootFormElementOp> getOp() {
        return TdHelpers.getValueIgnoringWrongType(wrappedObject, JsonFields.OP_MULTIPLE)
                .map(MultipleRootFormElementOp::fromJson)
                .map(RootFormElementOp.class::cast)
                .orElseGet(() -> wrappedObject.getValue(JsonFields.OP)
                        .flatMap(SingleRootFormElementOp::forName)
                        .orElseThrow(() -> WotValidationException
                                .newBuilder("The WoT TM/TD contained a root form element without 'op' member.")
                                .build())
                );
    }

    @Override
    protected RootFormElement createInstance(final JsonObject newWrapped) {
        return new ImmutableRootFormElement(newWrapped);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImmutableRootFormElement;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }
}
