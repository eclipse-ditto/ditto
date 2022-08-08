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

    static RootFormElement fromJson(final JsonObject jsonObject) {
        return new ImmutableRootFormElement(jsonObject);
    }

    static RootFormElement.Builder newBuilder() {
        return RootFormElement.Builder.newBuilder();
    }

    static RootFormElement.Builder newBuilder(final JsonObject jsonObject) {
        return RootFormElement.Builder.newBuilder(jsonObject);
    }

    @Override
    default RootFormElement.Builder toBuilder() {
        return RootFormElement.Builder.newBuilder(toJson());
    }

    RootFormElementOp<SingleRootFormElementOp> getOp();

    interface Builder extends FormElement.Builder<Builder, RootFormElement> {

        static Builder newBuilder() {
            return new MutableRootFormElementBuilder(JsonObject.newBuilder());
        }

        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableRootFormElementBuilder(jsonObject.toBuilder());
        }

        Builder setOp(@Nullable RootFormElementOp<SingleRootFormElementOp> op);

    }
}
