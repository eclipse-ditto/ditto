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
 * Mutable builder for {@link PropertyFormElement}s.
 */
final class MutablePropertyFormElementBuilder
        extends AbstractFormElementBuilder<PropertyFormElement.Builder, PropertyFormElement>
        implements PropertyFormElement.Builder {

    MutablePropertyFormElementBuilder(final JsonObjectBuilder wrappedObjectBuilder) {
        super(wrappedObjectBuilder, MutablePropertyFormElementBuilder.class);
    }

    @Override
    public PropertyFormElement.Builder setOp(@Nullable final PropertyFormElementOp<SinglePropertyFormElementOp> op) {
        if (op != null) {
            if (op instanceof MultiplePropertyFormElementOp) {
                putValue(FormElement.JsonFields.OP_MULTIPLE, ((MultiplePropertyFormElementOp) op).toJson());
            } else if (op instanceof SinglePropertyFormElementOp) {
                putValue(FormElement.JsonFields.OP, op.toString());
            }
        } else {
            remove(FormElement.JsonFields.OP);
        }
        return myself;
    }

    @Override
    public PropertyFormElement build() {
        return new ImmutablePropertyFormElement(wrappedObjectBuilder.build());
    }
}
