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
 * Mutable builder for {@link ActionFormElement}s.
 */
final class MutableActionFormElementBuilder
        extends AbstractFormElementBuilder<ActionFormElement.Builder, ActionFormElement>
        implements ActionFormElement.Builder {

    MutableActionFormElementBuilder(final JsonObjectBuilder wrappedObjectBuilder) {
        super(wrappedObjectBuilder, MutableActionFormElementBuilder.class);
    }

    @Override
    public ActionFormElement.Builder setOp(@Nullable final ActionFormElementOp<SingleActionFormElementOp> op) {
        if (op != null) {
            if (op instanceof MultipleActionFormElementOp) {
                putValue(FormElement.JsonFields.OP_MULTIPLE, ((MultipleActionFormElementOp) op).toJson());
            } else if (op instanceof SingleActionFormElementOp) {
                putValue(FormElement.JsonFields.OP, op.toString());
            }
        } else {
            remove(FormElement.JsonFields.OP);
        }
        return myself;
    }

    @Override
    public ActionFormElement build() {
        return new ImmutableActionFormElement(wrappedObjectBuilder.build());
    }
}
