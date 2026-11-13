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

import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.SqlExpression;

/**
 * The resolved SQL target of a single RQL sort key ({@code GetSortBsonVisitor}'s {@code path(...)} counterpart) — one of
 * two disjoint shapes, mirroring the two Postgres field worlds of §3.5:
 * <ul>
 *   <li>a ROOT-mapped doc-row column ({@code st.thing_id} / {@code st.namespace}) that is sorted directly, with NO
 *       lateral and NO auth (the two system columns are {@code NOT NULL} scalars — never arrays, never missing); or</li>
 *   <li>a slash-mapped / attribute / concrete-feature {@code wpath} whose boundary sort element is picked row-wise from
 *       a per-key {@code search_flat} lateral.</li>
 * </ul>
 * Exactly one of {@link #rootColumn()} / {@link #wpath()} is non-null. Wildcard feature sort paths never reach this type
 * — {@link GetSortSqlVisitor} rejects them (parity with Mongo, whose {@code GetSortBsonVisitor} has no wildcard branch).
 */
final class SortField {

    @Nullable
    private final SqlExpression rootColumn;
    @Nullable
    private final String wpath;

    private SortField(@Nullable final SqlExpression rootColumn, @Nullable final String wpath) {
        this.rootColumn = rootColumn;
        this.wpath = wpath;
    }

    static SortField ofRootColumn(final SqlExpression column) {
        return new SortField(Objects.requireNonNull(column, "column"), null);
    }

    static SortField ofFlatPath(final String wpath) {
        return new SortField(null, Objects.requireNonNull(wpath, "wpath"));
    }

    boolean isRoot() {
        return rootColumn != null;
    }

    SqlExpression rootColumn() {
        return Objects.requireNonNull(rootColumn, "rootColumn (this is a flat-path sort field)");
    }

    String wpath() {
        return Objects.requireNonNull(wpath, "wpath (this is a root-column sort field)");
    }
}
