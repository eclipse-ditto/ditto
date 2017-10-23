/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.json;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * An immutable JsonValue that wraps JSON NULL. Calling asObject() or asArray() returns a JSON NULL representation as an
 * instance of JsonObject, respectively JsonArray. This is different than the JSON literals because JSON NULL is a valid
 * value for a JSON field were expected is an JSON object or JSON array.
 */
@Immutable
final class ImmutableJsonNull extends AbstractMinimalJsonValueWrapper implements JsonNull {

    /**
     * Constructs a new {@code AbstractMinimalJsonValueWrapper} object.
     */
    private ImmutableJsonNull() {
        super(com.eclipsesource.json.Json.NULL);
    }

    /**
     * Returns a new instance of {@code ImmutableJsonNull}.
     *
     * @return a new ImmutableJsonNull.
     * @throws NullPointerException if {@code minimalJsonValue} is {@code null}.
     */
    public static JsonValue newInstance() {
        return new ImmutableJsonNull();
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

}
