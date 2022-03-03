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
 * Mutable builder for {@link RootFormElement}s.
 */
final class MutableRootFormElementBuilder
        extends AbstractFormElementBuilder<RootFormElement.Builder, RootFormElement>
        implements RootFormElement.Builder {

    MutableRootFormElementBuilder(final JsonObjectBuilder wrappedObjectBuilder) {
        super(wrappedObjectBuilder, MutableRootFormElementBuilder.class);
    }

    @Override
    public RootFormElement.Builder setOp(@Nullable final RootFormElementOp<SingleRootFormElementOp> op) {
        if (op != null) {
            if (op instanceof MultipleRootFormElementOp) {
                putValue(FormElement.JsonFields.OP_MULTIPLE, ((MultipleRootFormElementOp) op).toJson());
            } else if (op instanceof SingleRootFormElementOp) {
                putValue(FormElement.JsonFields.OP, op.toString());
            }
        } else {
            remove(FormElement.JsonFields.OP);
        }
        return myself;
    }

    @Override
    public RootFormElement build() {
        return new ImmutableRootFormElement(wrappedObjectBuilder.build());
    }
}
