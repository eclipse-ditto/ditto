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

import java.util.Optional;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.flatten.ThingFlattener;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * The search cursor's object sort boundary on PostgreSQL: both its wire form in the cursor ({@code {"typeRank":4}})
 * and the predicate operand the resume criteria compare against.
 * <p>
 * On the flat table every object-valued sort key orders as the same tuple ({@code type_rank} 4, no scalar value), so
 * all objects tie with each other and their order is decided by the trailing thing-id dimension alone (see
 * {@link PostgresSortClause}). A page that ends on an object therefore resumes by rank, then by thing id: the cursor
 * carries this marker instead of the object, and {@link CreateSqlPredicateVisitor} translates it as
 * </p>
 * <ul>
 *   <li>{@code eq}: a {@code type_rank = 4} probe on the sort key's path (an object row exists there);</li>
 *   <li>{@code gt}/{@code lt}: never (no object is strictly before or after another);</li>
 *   <li>{@code ge}/{@code le}/{@code ne}/{@code in}: rejected with {@link IllegalArgumentException}.</li>
 * </ul>
 * <p>
 * Only the search cursor creates this operand, and only under {@code gt}, {@code lt} and {@code eq}. A
 * {@code like}/{@code ilike} criterion receives its operand as a string ({@code toString()}), so the visitor cannot
 * reject this operand there; no caller builds one.
 * </p>
 * <p>
 * Encoder ({@link PostgresSortClause}) and decoder (the search persistence provider) share this one definition, so
 * the wire form cannot drift between them.
 * </p>
 *
 * @param typeRank the flat-table type rank of the boundary; only {@link ThingFlattener#TYPE_OBJECT} is supported.
 */
public record SortRankBoundary(int typeRank) {

    /** The single field of the cursor wire form. */
    static final String TYPE_RANK_FIELD = "typeRank";

    /**
     * @throws IllegalArgumentException if {@code typeRank} is not {@link ThingFlattener#TYPE_OBJECT}: only an object
     * boundary resumes by rank.
     */
    public SortRankBoundary {
        if (typeRank != ThingFlattener.TYPE_OBJECT) {
            throw new IllegalArgumentException("Only an object sort boundary (type rank " +
                    ThingFlattener.TYPE_OBJECT + ") is a resume operand, not type rank " + typeRank);
        }
    }

    /**
     * @return the object sort boundary.
     */
    public static SortRankBoundary object() {
        return new SortRankBoundary(ThingFlattener.TYPE_OBJECT);
    }

    /**
     * @return the cursor wire form {@code {"typeRank":<typeRank>}}.
     */
    public JsonObject toCursorValue() {
        return JsonObject.newBuilder().set(TYPE_RANK_FIELD, typeRank).build();
    }

    /**
     * Reads the cursor wire form back.
     *
     * @param cursorValue a cursor sort value.
     * @return the boundary if {@code cursorValue} is a JSON object with exactly the one int field {@code typeRank}
     * holding {@link ThingFlattener#TYPE_OBJECT}; empty for anything else.
     */
    public static Optional<SortRankBoundary> fromCursorValue(final JsonValue cursorValue) {
        if (!cursorValue.isObject()) {
            return Optional.empty();
        }
        final JsonObject object = cursorValue.asObject();
        if (object.getSize() != 1) {
            return Optional.empty();
        }
        return object.getValue(TYPE_RANK_FIELD)
                .filter(JsonValue::isInt)
                .map(JsonValue::asInt)
                .filter(rank -> rank == ThingFlattener.TYPE_OBJECT)
                .map(SortRankBoundary::new);
    }

}
