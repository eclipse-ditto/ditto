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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.flatten;

import java.math.BigDecimal;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * One {@code search_flat} row produced by {@link ThingFlattener}, shaped exactly for the {@code search_flat} table
 * columns (plan §3.2, {@code PostgresSearchSchema}): {@code (thing_id, path, wpath, f_id, ord, type_rank, val_bool,
 * val_num, val_text)}, in that order, so Task C2's writer can column-wise {@code unnest()} a batch of rows without
 * any reshaping.
 * <p>
 * Exactly one of {@link #valBool()}/{@link #valNum()}/{@link #valText()} is non-null for a scalar row
 * ({@code typeRank} 2/3/6); all three are {@code null} for object/array/null rows ({@code typeRank} 1/4/5).
 *
 * @param thingId the owning thing's ID, copied verbatim from the source {@code SearchIndexDocument}.
 * @param path the RFC-6901-escaped JSON-pointer path over the thing payload (see {@link ThingFlattener} for the
 * escaping rule) — never rewritten for feature wildcarding, unlike {@link #wpath()}.
 * @param wpath the queryable path — equal to {@code path} outside a feature subtree, or the
 * {@code /features/*}-wildcarded variant of {@code path} inside one.
 * @param fId the feature ID (raw, not RFC-6901-escaped — it is a semantic identifier, not a path segment) if this
 * row is inside a feature subtree, else {@code null}.
 * @param ord the row-enumeration counter for this {@code (path, wpath)} pair within the thing (not an array
 * index — see {@link ThingFlattener}). Both rows of a dual-wpath feature-subtree emission share the same {@code ord}.
 * @param typeRank 1 null, 2 number, 3 string, 4 object, 5 array, 6 boolean.
 * @param valBool the boolean value if {@code typeRank == 6}, else {@code null}.
 * @param valNum the numeric value if {@code typeRank == 2}, else {@code null}.
 * @param valText the string value if {@code typeRank == 3}, else {@code null}.
 * @since 4.0.0
 */
@Immutable
public record FlatRow(
        String thingId,
        String path,
        String wpath,
        @Nullable String fId,
        int ord,
        short typeRank,
        @Nullable Boolean valBool,
        @Nullable BigDecimal valNum,
        @Nullable String valText) {

    public FlatRow {
        Objects.requireNonNull(thingId, "thingId");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(wpath, "wpath");
    }
}
