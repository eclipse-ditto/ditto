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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast;

import java.util.LinkedHashMap;
import java.util.Map;

import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;

import org.reactivestreams.Publisher;

/**
 * A no-DB {@link Statement} stub that records positional {@code bind}/{@code bindNull} calls, so
 * {@link RenderedSql#applyTo} can be unit-tested (correct type hints, correct null handling, correct order) without a
 * real connection.
 */
final class RecordingStatement implements Statement {

    final Map<Integer, Object> bound = new LinkedHashMap<>();
    final Map<Integer, Class<?>> boundNull = new LinkedHashMap<>();

    @Override
    public Statement add() {
        return this;
    }

    @Override
    public Statement bind(final int index, final Object value) {
        bound.put(index, value);
        return this;
    }

    @Override
    public Statement bind(final String name, final Object value) {
        throw new UnsupportedOperationException("named binds are not used by RenderedSql#applyTo");
    }

    @Override
    public Statement bindNull(final int index, final Class<?> type) {
        boundNull.put(index, type);
        return this;
    }

    @Override
    public Statement bindNull(final String name, final Class<?> type) {
        throw new UnsupportedOperationException("named binds are not used by RenderedSql#applyTo");
    }

    @Override
    public Publisher<? extends Result> execute() {
        throw new UnsupportedOperationException("RecordingStatement does not execute");
    }

}
