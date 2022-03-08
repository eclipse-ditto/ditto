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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;

/**
 * Abstract implementation of {@link TypedJsonObject}.
 */
abstract class AbstractTypedJsonObject<T extends TypedJsonObject<T>> implements TypedJsonObject<T> {

    protected final JsonObject wrappedObject;

    protected AbstractTypedJsonObject(final JsonObject wrappedObject) {
        this.wrappedObject = checkNotNull(wrappedObject, "wrappedObject");
    }

    @Override
    public JsonObject getWrappedObject() {
        return wrappedObject;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T determineResult(final Supplier<JsonObject> newWrappedSupplier) {
        final JsonObject newWrapped = newWrappedSupplier.get();
        if (!newWrapped.equals(wrappedObject)) {
            return createInstance(newWrapped);
        }
        return (T) this;
    }

    /**
     * Creates a new instance of this typed JsonObject based on the provided {@code newWrapped} JsonObject.
     *
     * @param newWrapped the new JsonObject to wrap in the new created instance.
     * @return a new instance of this typed JsonObject.
     */
    protected abstract T createInstance(JsonObject newWrapped);

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractTypedJsonObject<?> that = (AbstractTypedJsonObject<?>) o;
        return that.canEqual(this) && Objects.equals(wrappedObject, that.wrappedObject);
    }

    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractTypedJsonObject;
    }

    @Override
    public int hashCode() {
        return Objects.hash(wrappedObject);
    }

    @Override
    public String toString() {
        return "wrappedObject=" + wrappedObject;
    }
}
