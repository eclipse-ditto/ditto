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

import javax.annotation.Nullable;

/**
 * One bind parameter of a rendered SQL statement: the value (possibly {@code null}) paired with its r2dbc type hint. The
 * position in {@link RenderedSql#binds()} is its 1-based {@code $n} number.
 *
 * @param value the value to bind; {@code null} binds a typed SQL {@code NULL}.
 * @param type the r2dbc type hint driving the {@code $n} cast and the {@code bindNull} type.
 */
public record SqlBind(@Nullable Object value, SqlBindType type) {

    public SqlBind {
        if (type == null) {
            throw new NullPointerException("SqlBind type must not be null");
        }
    }
}
