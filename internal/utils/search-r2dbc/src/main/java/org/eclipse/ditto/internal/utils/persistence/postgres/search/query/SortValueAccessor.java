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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.query;

import javax.annotation.Nullable;

/**
 * A minimal, backend-neutral column reader over a single result row — the seam {@link PostgresSortClause#toSortValues}
 * uses to project the last hit's sort values for cursor encoding, WITHOUT this query module depending on the r2dbc
 * {@code Row} type. Its single method is signature-compatible with {@code io.r2dbc.spi.Row#get(String, Class)}, so the
 * D4 read persistence adapts a row with a method reference ({@code row::get}); a unit test adapts a plain map.
 */
@FunctionalInterface
public interface SortValueAccessor {

    /**
     * @param columnAlias the projected column alias (one of the aliases {@link PostgresSortClause#applyCursorProjections}
     * assigns).
     * @param type the requested Java type.
     * @param <T> the requested type.
     * @return the column value, or {@code null} if the column is SQL {@code NULL}.
     */
    @Nullable
    <T> T get(String columnAlias, Class<T> type);
}
