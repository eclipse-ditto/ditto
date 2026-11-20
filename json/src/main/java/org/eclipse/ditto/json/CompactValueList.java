/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.util.AbstractList;
import java.util.RandomAccess;

import javax.annotation.concurrent.Immutable;

/**
 * Immutable list of the values of a parsed JSON array, backed by an exactly sized array which it takes over -
 * without the wrapper and the spare capacity of an unmodifiable {@code ArrayList}.
 */
@Immutable
final class CompactValueList extends AbstractList<JsonValue> implements RandomAccess {

    private final JsonValue[] values;

    CompactValueList(final JsonValue[] values) {
        this.values = values;
    }

    @Override
    public JsonValue get(final int index) {
        return values[index];
    }

    @Override
    public int size() {
        return values.length;
    }

}
