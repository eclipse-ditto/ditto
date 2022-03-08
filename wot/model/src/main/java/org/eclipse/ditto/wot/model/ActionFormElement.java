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

    static ActionFormElement fromJson(final JsonObject jsonObject) {
        return new ImmutableActionFormElement(jsonObject);
    }

    static ActionFormElement.Builder newBuilder() {
        return ActionFormElement.Builder.newBuilder();
    }

    static ActionFormElement.Builder newBuilder(final JsonObject jsonObject) {
        return ActionFormElement.Builder.newBuilder(jsonObject);
    }

    @Override
    default ActionFormElement.Builder toBuilder() {
        return ActionFormElement.Builder.newBuilder(toJson());
    }

    ActionFormElementOp<SingleActionFormElementOp> getOp();

    interface Builder extends FormElement.Builder<Builder, ActionFormElement> {

        static Builder newBuilder() {
            return new MutableActionFormElementBuilder(JsonObject.newBuilder());
        }

        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableActionFormElementBuilder(jsonObject.toBuilder());
        }

        Builder setOp(@Nullable ActionFormElementOp<SingleActionFormElementOp> op);

    }

}
