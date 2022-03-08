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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonValue;

/**
 * Abstract implementation of {@link MultipleFormElementOp}.
 */
abstract class AbstractMultipleFormElementOp<O extends FormElementOp<O>> implements MultipleFormElementOp<O> {

    private final List<O> ops;

    protected AbstractMultipleFormElementOp(final Collection<O> ops) {
        this.ops = Collections.unmodifiableList(new ArrayList<>(ops));
    }

    @Override
    public Iterator<O> iterator() {
        return ops.iterator();
    }

    @Override
    public JsonArray toJson() {
        return ops.stream()
                .map(String::valueOf)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractMultipleFormElementOp<?> that = (AbstractMultipleFormElementOp<?>) o;
        return canEqual(that) && Objects.equals(ops, that.ops);
    }

    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractMultipleFormElementOp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ops);
    }

    @Override
    public String toString() {
        return "ops=" + ops;
    }
}
