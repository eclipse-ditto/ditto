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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.flatten.ThingFlattener;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Select;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Sql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.SqlExpression;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.SortOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Postgres sort/paging translation (Task D3): turns a query's ordered {@link SortOption}s into the §3.5 sort lateral
 * blocks, the outer {@code ORDER BY} assembly, and the cursor-value projection — the pieces D4's {@code findAll} SELECT
 * bolts onto its {@code SELECT … FROM search_things st WHERE …} skeleton. It carries NO auth (sort keys are never
 * authorization-filtered — §3.3 exception 3).
 * <p>
 * <b>The four §3.5 sort-parity requirements, and where each lives:</b>
 * <ul>
 *   <li><b>(a) missing == null.</b> A {@code LEFT JOIN LATERAL … ON true} keeps things whose sort key is absent (all
 *       lateral columns come back {@code NULL}); the outer rank expression {@code COALESCE(…, 1)} folds that {@code
 *       NULL} into the null rank ({@link ThingFlattener#TYPE_NULL}), so missing ties with an explicit null exactly as
 *       {@code ThingsSearchCursor.getNextDimensionCriteria} hard-codes. Plain {@code NULLS FIRST} would sort missing
 *       STRICTLY before null — a verified divergence, deliberately not used.</li>
 *   <li><b>(b) arrays sort by their MIN element ascending / MAX element descending, chosen ROW-WISE.</b> The lateral's
 *       inner {@code ORDER BY type_rank[…] , val_num[…], val_text[…], val_bool[…] LIMIT 1} picks the boundary element as
 *       ONE whole row (min-first for asc, max-first for desc) — never per-column {@code min()}/{@code max()} aggregates,
 *       which would splice a rank from one element onto a value from another (the "chimera tuple" hazard) on a
 *       mixed-type array.</li>
 *   <li><b>(c) {@code val_bool} is part of the ordering tuple.</b> Rank-6 (boolean) rows carry {@code NULL} val_num /
 *       val_text; without val_bool in the outer {@code ORDER BY} the false/true order would collapse onto the thing-id
 *       tiebreak. Postgres {@code false < true} matches BSON {@code false < true}.</li>
 *   <li><b>(d) empty arrays map to the null rank.</b> A literal empty array flattens to a single valueless {@code
 *       type_rank = TYPE_ARRAY (5)} stub row; the outer rank expression's {@code CASE WHEN … = 5 THEN 1} folds it into
 *       the null rank, so an empty array ties with null/missing in sorts (verified on mongo:7), even though {@code
 *       eq(path,null)} does NOT match {@code []}.</li>
 * </ul>
 * <b>Direction (Mongo semantics — the whole key reverses):</b> a descending sort key applies {@code DESC} to BOTH the
 * inner boundary selection (so the MAX element is picked) AND every outer {@code ORDER BY} component (rank, val_num,
 * val_text, val_bool). Because {@code COALESCE(…,1)} maps missing/null/empty to the SMALLEST rank, under {@code DESC}
 * they sort LAST — matching Mongo's descending null placement.
 * <p>
 * <b>Trailing thing-id tiebreak.</b> There is deliberately NO synthetic tiebreak here: {@link PostgresQueryBuilder}'s
 * thing-id truncation guarantees the sort-option list ALWAYS ends with the {@code _id} option (a root-mapped column),
 * so the {@code ORDER BY} already terminates in {@code st.thing_id} — the total-order/keyset tiebreak — as its last
 * component. Adding one here would double it. This class cannot enforce that invariant structurally (it has no
 * reference back to the builder), so {@link #of} only WARNS, never throws, when handed a list that does not end
 * there — see {@link #of}'s javadoc.
 * </p>
 * <p>
 * <b>Documented divergences vs MongoDB sort</b> (verified against this class's CASE/COALESCE logic, {@link
 * GetSortSqlVisitor}'s wildcard rejection and {@link ThingFlattener}'s rank emissions — these are NOT bugs, they are
 * known, intentional gaps the parity IT (plan Phase G) must allow-list rather than chase):
 * <ul>
 *   <li><b>Object-valued sort keys tie with EACH OTHER, not with null.</b> An object row's {@code type_rank} is
 *       {@link ThingFlattener#TYPE_OBJECT} (4) with {@code val_num}/{@code val_text}/{@code val_bool} all {@code
 *       NULL}. {@link #appendOrderBy}'s {@code CASE} only folds rank {@link ThingFlattener#TYPE_ARRAY} (5) into the
 *       null rank (1); rank 4 passes through unchanged. So every object-valued thing produces the identical ordering
 *       tuple {@code (4, NULL, NULL, NULL)} — they tie with one another (NOT with missing/null/empty-array, which
 *       fold to rank 1) and their relative order among themselves is decided entirely by the trailing thing-id
 *       tiebreak. MongoDB instead orders objects by their BSON field content, recursively — so a result set with more
 *       than one object-valued thing on the sort key can come back in a different relative order between the two
 *       backends.</li>
 *   <li><b>Direct array-of-array sort keys are ASC/DESC-asymmetric when a scalar sibling shares the same path.</b>
 *       {@link ThingFlattener} emits a direct array-of-array element as a single valueless {@code type_rank = 5} stub
 *       (see its class javadoc, "Arrays") instead of descending into it — the §3.2 array-of-array stub rule this
 *       class's cursor-value divergence (see {@link #toSortValues}) already documents on the READ side. If the same
 *       wpath ALSO has a scalar sibling row at some other array index (e.g. {@code x: [[1, 2], "z"]} — the {@code
 *       "z"} element walks normally to a {@code type_rank = }{@link ThingFlattener#TYPE_STRING} row), the lateral's
 *       {@code ORDER BY type_rank[, …] LIMIT 1} (rank order NULL=1 &lt; NUMBER=2 &lt; STRING=3 &lt; OBJECT=4 &lt;
 *       ARRAY=5 &lt; BOOLEAN=6) picks a DIFFERENT row per direction: ascending picks the lower-ranked scalar sibling
 *       (rank 5 is never the {@code MIN} unless every element at that path is itself an array) and sorts by that
 *       scalar's actual value, while descending picks the rank-5 array stub itself (it outranks NULL/NUMBER/STRING/
 *       OBJECT siblings, though NOT a rank-6 boolean sibling) — which the outer {@code CASE} then folds into the null
 *       rank, so a descending sort treats the thing as if the key were missing even though an ascending sort on the
 *       very same row orders it by a real scalar value. MongoDB sorts consistently by the inner array's own elements
 *       in both directions; it has no such fold.</li>
 *   <li><b>Wildcard feature sort paths are REJECTED, not silently ordered.</b> {@link GetSortSqlVisitor} throws
 *       {@link IllegalArgumentException} for a {@code features/*}-shaped sort key. {@code GetSortBsonVisitor} has no
 *       wildcard branch either, but because it only ever builds a dotted BSON path string, a wildcard feature id
 *       degrades SILENTLY into a literal {@code "*"} path segment (e.g. {@code thing.features.*.properties.x}) that
 *       matches no real document field — an order-less, effectively no-op "sort" that still returns results, just
 *       unordered by that key (verified against {@code GetSortBsonVisitor#visitFeatureIdProperty} /
 *       {@code MongoSortKeyMappingFunction}, which pass the feature id through uninspected). On Postgres the
 *       wildcarded {@code /features/*} wpath is a REAL {@code search_flat} row family the flattener emits for
 *       cross-feature auth fan-out (see {@code ThingFlattener}'s "Dual-wpath" note), so silently sorting by it would
 *       aggregate across every feature on the thing — a worse divergence than failing loudly. This
 *       stricter-than-Mongo behavior is sanctioned by plan §3.5's "needn't support" note on wildcard sort.</li>
 * </ul>
 */
public final class PostgresSortClause {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresSortClause.class);

    private static final String FLAT_TABLE = AbstractFieldSqlCreator.FLAT_TABLE;
    private static final String DOC_ALIAS = AbstractFieldSqlCreator.DOC_ALIAS;
    private static final String FLAT_ALIAS = "s";
    private static final String THING_ID = AbstractFieldSqlCreator.THING_ID;
    private static final String PATH = AbstractFieldSqlCreator.PATH;
    private static final String WPATH = AbstractFieldSqlCreator.WPATH;
    private static final String TYPE_RANK = AbstractFieldSqlCreator.TYPE_RANK;

    /** Fixed integer literals (fixed grammar, not data — never a bind): the two ranks the sort {@code CASE} folds. */
    private static final SqlExpression RANK_ARRAY_LITERAL = Sql.raw(Short.toString(ThingFlattener.TYPE_ARRAY));
    private static final SqlExpression RANK_NULL_LITERAL = Sql.raw(Short.toString(ThingFlattener.TYPE_NULL));
    private static final SqlExpression ONE = Sql.raw("1");

    /**
     * The rendered form of the doc-row thing-id column ({@code st.thing_id}) — the total-order tiebreak {@link
     * #of}'s defensive check expects the LAST sort key to resolve to. Rendered once (not data, never a bind) so the
     * check below is a plain string comparison instead of re-deriving/re-rendering a fresh {@link SqlExpression}
     * every call.
     */
    private static final String THING_ID_TIEBREAK_SQL = Sql.render(Sql.col(DOC_ALIAS, THING_ID)).sql();

    private final List<Key> keys;

    private PostgresSortClause(final List<Key> keys) {
        this.keys = List.copyOf(keys);
    }

    /**
     * @param sortOptions the query's ordered sort options (already thing-id-truncated by {@link PostgresQueryBuilder}
     * — this factory RELIES ON, but cannot itself enforce, that invariant; see {@link #warnIfNotThingIdTerminated}).
     * @return the sort clause.
     * @throws IllegalArgumentException if a sort option resolves to a wildcard-feature path (see {@link
     * GetSortSqlVisitor}).
     */
    public static PostgresSortClause of(final List<SortOption> sortOptions) {
        Objects.requireNonNull(sortOptions, "sortOptions");
        final List<Key> keys = new ArrayList<>(sortOptions.size());
        for (int i = 0; i < sortOptions.size(); i++) {
            final SortOption option = sortOptions.get(i);
            keys.add(new Key(i, GetSortSqlVisitor.apply(option.getSortExpression()),
                    option.getSortDirection() == SortDirection.DESC));
        }
        warnIfNotThingIdTerminated(keys);
        return new PostgresSortClause(keys);
    }

    /**
     * Defensive (non-throwing) invariant check: {@link PostgresQueryBuilder#sort} guarantees every assembled
     * sort-option list is thing-id-truncated, i.e. its last entry is the {@code _id} root key, so the {@code ORDER
     * BY} {@link #appendOrderBy} assembles always terminates in the {@code st.thing_id} total-order/keyset tiebreak
     * (class javadoc, "Trailing thing-id tiebreak"). This class has no reference back to the builder to enforce that
     * at construction time, so a caller CAN hand it a list that violates it — most commonly this module's own unit
     * tests, which legitimately build single- or few-key lists (deliberately omitting the tiebreak) to isolate one
     * sort key's SQL translation. A hard failure here would reject that valid test usage along with genuine
     * production misuse, so this only WARNS: a query built without going through {@link PostgresQueryBuilder} loses
     * the total order (ties become non-deterministic / keyset paging breaks), but it is not this class's place to
     * crash the request over it.
     *
     * @param keys the resolved sort keys, in the same order as the {@code sortOptions} passed to {@link #of}.
     */
    private static void warnIfNotThingIdTerminated(final List<Key> keys) {
        if (keys.isEmpty()) {
            return;
        }
        final Key last = keys.get(keys.size() - 1);
        final boolean endsInThingId = last.field.isRoot()
                && THING_ID_TIEBREAK_SQL.equals(Sql.render(last.field.rootColumn()).sql());
        if (!endsInThingId && LOGGER.isWarnEnabled()) {
            LOGGER.warn("Sort-option list does not end in the thing-id tiebreak ({}); the assembled ORDER BY has no "
                    + "guaranteed total order (ties are non-deterministic, keyset paging can break). Expected "
                    + "callers (PostgresQueryBuilder#sort) always thing-id-truncate the sort-option list before "
                    + "reaching PostgresSortClause#of.", THING_ID_TIEBREAK_SQL);
        }
    }

    /**
     * Attaches, to a {@code SELECT … FROM search_things st [WHERE …]} builder, one {@code LEFT JOIN LATERAL} per
     * flat-path sort key (root-mapped keys need none) and the full outer {@code ORDER BY} assembly. Call after the
     * builder's {@code FROM}/{@code WHERE} are set.
     *
     * @param builder the query builder to extend.
     */
    public void applyOrdering(final Select.Builder builder) {
        Objects.requireNonNull(builder, "builder");
        for (final Key key : keys) {
            if (!key.field.isRoot()) {
                builder.leftJoinLateral(lateral(key), lateralAlias(key.index));
            }
        }
        for (final Key key : keys) {
            appendOrderBy(builder, key);
        }
    }

    /**
     * Adds the projected columns D4 must read back to compute the last hit's cursor sort values (see {@link
     * #toSortValues}). One aliased column per root key ({@code s<i>_val}); four per flat key ({@code s<i>_rank},
     * {@code s<i>_num}, {@code s<i>_text}, {@code s<i>_bool}). Call on the same builder {@link #applyOrdering} extended,
     * so the {@code k<i>} lateral aliases the flat columns reference exist.
     *
     * @param builder the query builder to extend.
     */
    public void applyCursorProjections(final Select.Builder builder) {
        Objects.requireNonNull(builder, "builder");
        for (final Key key : keys) {
            if (key.field.isRoot()) {
                builder.column(key.field.rootColumn(), valAlias(key.index));
            } else {
                final String k = lateralAlias(key.index);
                builder.column(Sql.col(k, TYPE_RANK), rankAlias(key.index));
                builder.column(Sql.col(k, SqlValues.VAL_NUM), numAlias(key.index));
                builder.column(Sql.col(k, SqlValues.VAL_TEXT), textAlias(key.index));
                builder.column(Sql.col(k, SqlValues.VAL_BOOL), boolAlias(key.index));
            }
        }
    }

    /**
     * Projects the sort values of a single (the last-of-page) result row into the {@link JsonArray} {@code
     * ThingsSearchCursor} encodes — the Postgres counterpart of {@code GetSortBsonVisitor.sortValuesAsArray}. Each
     * element is the boundary element's type-faithful JSON: a number for rank-2, a string for rank-3, a boolean for
     * rank-6, and {@code null} for a missing key, an explicit null (rank-1), an empty array (rank-5) OR an object
     * (rank-4) boundary (the latter two have no scalar value on the flat table — a documented divergence from Mongo's
     * whole-container cursor value, §3.5). Root-mapped keys yield their text column value.
     *
     * @param row the result-row accessor (the D4 read persistence adapts an r2dbc {@code Row} with {@code row::get}).
     * @return the sort values, in sort-key order.
     */
    public JsonArray toSortValues(final SortValueAccessor row) {
        Objects.requireNonNull(row, "row");
        final List<JsonValue> values = new ArrayList<>(keys.size());
        for (final Key key : keys) {
            if (key.field.isRoot()) {
                values.add(textValue(row.get(valAlias(key.index), String.class)));
            } else {
                values.add(flatBoundaryValue(
                        row.get(rankAlias(key.index), Integer.class),
                        row.get(numAlias(key.index), BigDecimal.class),
                        row.get(textAlias(key.index), String.class),
                        row.get(boolAlias(key.index), Boolean.class)));
            }
        }
        return JsonFactory.newArrayBuilder(values).build();
    }

    // ---- lateral + order-by assembly ------------------------------------------------------------------------------

    /**
     * The per-key sort lateral. Besides {@code wpath} it binds {@code path} to the same sort wpath, which is exact
     * rather than a heuristic: a sort wpath is never {@code /features/*}-wildcarded ({@link GetSortSqlVisitor} rejects
     * a {@code *} feature id, and {@link #requireConcreteWpath} rejects any other route to such a wpath), and {@link
     * ThingFlattener} writes every non-wildcard row with {@code wpath = path} (only the {@code /features/*} twin of a
     * feature row carries a different wpath). So {@code path = $sortpath} selects exactly the rows {@code wpath =
     * $sortpath} selects. It lets the probe use all three leading columns of the {@code (thing_id, path, wpath, ord)}
     * primary key: it descends straight to the sort key's rows instead of reading every row of the thing and filtering
     * on {@code wpath}, and the planner's per-probe cost estimate shrinks accordingly. On the production-config 1M-thing
     * search bench, page 1 and OFFSET pages took ~40% less time, partly because the plan cost then stayed below the
     * JIT optimize/inline thresholds.
     */
    private Select lateral(final Key key) {
        final String wpath = requireConcreteWpath(key.field.wpath());
        // SELECT s.type_rank, s.val_num, s.val_text, s.val_bool FROM search_flat s
        //   WHERE s.thing_id = st.thing_id AND s.path = $sortpath AND s.wpath = $sortpath
        //   ORDER BY type_rank[…], val_num[…], val_text[…], val_bool[…] LIMIT 1  -- boundary element, row-wise (§3.5b)
        final Select.Builder builder = Sql.select()
                .column(Sql.col(FLAT_ALIAS, TYPE_RANK))
                .column(Sql.col(FLAT_ALIAS, SqlValues.VAL_NUM))
                .column(Sql.col(FLAT_ALIAS, SqlValues.VAL_TEXT))
                .column(Sql.col(FLAT_ALIAS, SqlValues.VAL_BOOL))
                .from(FLAT_TABLE, FLAT_ALIAS)
                .where(Sql.and(
                        Sql.eq(Sql.col(FLAT_ALIAS, THING_ID), Sql.col(DOC_ALIAS, THING_ID)),
                        Sql.eq(Sql.col(FLAT_ALIAS, PATH), Sql.text(wpath)),
                        Sql.eq(Sql.col(FLAT_ALIAS, WPATH), Sql.text(wpath))));
        orderByDirected(builder, Sql.col(FLAT_ALIAS, TYPE_RANK), key.descending);
        orderByDirected(builder, Sql.col(FLAT_ALIAS, SqlValues.VAL_NUM), key.descending);
        orderByDirected(builder, Sql.col(FLAT_ALIAS, SqlValues.VAL_TEXT), key.descending);
        orderByDirected(builder, Sql.col(FLAT_ALIAS, SqlValues.VAL_BOOL), key.descending);
        return builder.limit(ONE).build();
    }

    /**
     * Fails loudly when a {@code /features/*} wpath reaches the lateral. {@link GetSortSqlVisitor} rejects a {@code *}
     * feature id, but a configured simple-field mapping can still name such a wpath directly. No row has {@code path}
     * equal to a {@code /features/*} wpath, so the lateral would silently sort every thing as if the key were missing.
     *
     * @param wpath the sort key's wpath.
     * @return {@code wpath}, if it is concrete.
     * @throws IllegalArgumentException if {@code wpath} is a {@code /features/*} wildcard wpath.
     */
    private static String requireConcreteWpath(final String wpath) {
        if (wpath.equals(AbstractFieldSqlCreator.FEATURES_WILDCARD_PREFIX)
                || wpath.startsWith(AbstractFieldSqlCreator.FEATURES_WILDCARD_PREFIX + "/")) {
            throw new IllegalArgumentException("Sorting on wildcard feature paths (features/*/...) is not supported, "
                    + "got sort wpath <" + wpath + ">.");
        }
        return wpath;
    }

    private void appendOrderBy(final Select.Builder builder, final Key key) {
        if (key.field.isRoot()) {
            // A NOT-NULL scalar system column — sorted directly, no rank/value tuple, no COALESCE/CASE.
            orderByDirected(builder, key.field.rootColumn(), key.descending);
            return;
        }
        final String k = lateralAlias(key.index);
        // COALESCE(CASE WHEN k.type_rank = 5 THEN 1 ELSE k.type_rank END, 1) — empty-array→null-rank (d) + missing→null-rank (a).
        final SqlExpression rank = Sql.coalesce(
                Sql.caseWhen(List.of(Sql.when(Sql.eq(Sql.col(k, TYPE_RANK), RANK_ARRAY_LITERAL), RANK_NULL_LITERAL)),
                        Sql.col(k, TYPE_RANK)),
                ONE);
        orderByDirected(builder, rank, key.descending);
        orderByDirected(builder, Sql.col(k, SqlValues.VAL_NUM), key.descending);
        orderByDirected(builder, Sql.col(k, SqlValues.VAL_TEXT), key.descending);
        orderByDirected(builder, Sql.col(k, SqlValues.VAL_BOOL), key.descending);
    }

    private static void orderByDirected(final Select.Builder builder, final SqlExpression expression,
            final boolean descending) {
        if (descending) {
            builder.orderByDesc(expression);
        } else {
            builder.orderByAsc(expression);
        }
    }

    // ---- cursor value extraction ----------------------------------------------------------------------------------

    private static JsonValue flatBoundaryValue(@Nullable final Integer typeRank, @Nullable final BigDecimal valNum,
            @Nullable final String valText, @Nullable final Boolean valBool) {
        if (typeRank == null) {
            return JsonFactory.nullLiteral();
        }
        return switch (typeRank.shortValue()) {
            case ThingFlattener.TYPE_NUMBER -> numberValue(valNum);
            case ThingFlattener.TYPE_STRING -> textValue(valText);
            case ThingFlattener.TYPE_BOOLEAN -> valBool == null ? JsonFactory.nullLiteral() : JsonValue.of(valBool);
            // TYPE_NULL(1), TYPE_OBJECT(4), TYPE_ARRAY(5): no scalar cursor value (§3.5 documented divergence).
            default -> JsonFactory.nullLiteral();
        };
    }

    private static JsonValue numberValue(@Nullable final BigDecimal value) {
        if (value == null) {
            return JsonFactory.nullLiteral();
        }
        // int/long preserved when the value is integral and fits a long (matches ThingFlattener.toBigDecimal); else
        // double. Ditto numbers are int/long/double only, so numeric round-trip is exact (plan §3.5 cursor row).
        final BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            try {
                return JsonValue.of(stripped.longValueExact());
            } catch (final ArithmeticException outOfLongRange) {
                return JsonValue.of(value.doubleValue());
            }
        }
        return JsonValue.of(value.doubleValue());
    }

    private static JsonValue textValue(@Nullable final String value) {
        // JsonFactory.newValue is exactly what GetSortBsonVisitor.toJsonValue reaches for a CharSequence (parity).
        return value == null ? JsonFactory.nullLiteral() : JsonFactory.newValue(value);
    }

    // ---- alias vocabulary -----------------------------------------------------------------------------------------

    private static String lateralAlias(final int index) {
        return "k" + index;
    }

    private static String valAlias(final int index) {
        return "s" + index + "_val";
    }

    private static String rankAlias(final int index) {
        return "s" + index + "_rank";
    }

    private static String numAlias(final int index) {
        return "s" + index + "_num";
    }

    private static String textAlias(final int index) {
        return "s" + index + "_text";
    }

    private static String boolAlias(final int index) {
        return "s" + index + "_bool";
    }

    private static final class Key {

        private final int index;
        private final SortField field;
        private final boolean descending;

        private Key(final int index, final SortField field, final boolean descending) {
            this.index = index;
            this.field = field;
            this.descending = descending;
        }
    }
}
