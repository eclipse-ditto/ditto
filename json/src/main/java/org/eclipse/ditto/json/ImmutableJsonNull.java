/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.json;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * An immutable JsonValue that wraps JSON NULL.
 * Calling {@link #asObject()} or {@link #asArray()} returns a JSON NULL representation as an instance of JsonObject,
 * respectively JsonArray.
 * This is different than the JSON literals because JSON NULL is a valid value for a JSON field were expected is an
 * JSON object or JSON array.
 */
@Immutable
final class ImmutableJsonNull extends AbstractJsonValue implements JsonNull {

    private static final ImmutableJsonNull INSTANCE = new ImmutableJsonNull();

    /**
     * Constructs a new {@code AbstractMinimalJsonValueWrapper} object.
     */
    private ImmutableJsonNull() {
        super();
    }

    /**
     * Returns an instance of {@code ImmutableJsonNull}.
     *
     * @return the instance.
     */
    public static ImmutableJsonNull getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isNull() {
        return true;
    }

    @Override
    public boolean isObject() {
        return true;
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public JsonObject asObject() {
        return JsonFactory.nullObject();
    }

    @Override
    public JsonArray asArray() {
        return JsonFactory.nullArray();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        return this == o || null != o && (super.equals(o) || o instanceof JsonNull);
    }

    @Override
    public int hashCode() {
        return JsonNull.class.hashCode();
    }

    @Override
    public String toString() {
        return "null";
    }

}
