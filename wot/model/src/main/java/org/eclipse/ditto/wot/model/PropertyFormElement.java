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
 * An PropertyFormElement is a FormElement defined in {@link Property}s.
 *
 * @since 2.4.0
 */
public interface PropertyFormElement extends FormElement<PropertyFormElement> {

    static PropertyFormElement fromJson(final JsonObject jsonObject) {
        return new ImmutablePropertyFormElement(jsonObject);
    }

    static PropertyFormElement.Builder newBuilder() {
        return PropertyFormElement.Builder.newBuilder();
    }

    static PropertyFormElement.Builder newBuilder(final JsonObject jsonObject) {
        return PropertyFormElement.Builder.newBuilder(jsonObject);
    }

    @Override
    default PropertyFormElement.Builder toBuilder() {
        return PropertyFormElement.Builder.newBuilder(toJson());
    }
    
    PropertyFormElementOp<SinglePropertyFormElementOp> getOp();

    interface Builder extends FormElement.Builder<Builder, PropertyFormElement> {

        static Builder newBuilder() {
            return new MutablePropertyFormElementBuilder(JsonObject.newBuilder());
        }

        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutablePropertyFormElementBuilder(jsonObject.toBuilder());
        }

        Builder setOp(@Nullable PropertyFormElementOp<SinglePropertyFormElementOp> op);

    }

}
