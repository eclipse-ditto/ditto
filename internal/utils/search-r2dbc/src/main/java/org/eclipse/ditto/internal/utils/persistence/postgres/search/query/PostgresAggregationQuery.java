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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.RenderedSql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Select;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Sql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.SqlExpression;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.rql.query.criteria.Criteria;

/**
 * The SQL counterpart of {@code MongoThingsAggregationPersistence}'s {@code $match}+{@code $group} pipeline (operator
 * "custom aggregation metrics", plan §3.5). Pure and DB-free: it renders a fully-parameterized {@link RenderedSql}
 * (unit-testable without a database) and, symmetrically, maps a result row back into the exact backend-neutral
 * {@link JsonObject} the consumer ({@code AggregateThingsMetricsResponse}) expects.
 * <p>
 * <b>Pipeline → SQL transcription (Mongo {@code aggregateThings} line refs):</b>
 * <pre>{@code
 *   SELECT (st.thing #>> $g0::text[]) AS g0, …, count(*) AS count
 *   FROM search_things st
 *   WHERE [st.namespace = ANY($ns::text[]) AND] <sudo-translated filter>
 *   GROUP BY 1, 2, …
 * }</pre>
 * <ul>
 *   <li>{@code $match} namespace {@code in(FIELD_NAMESPACE, namespaces)} (Mongo:104-106) → {@code st.namespace = ANY(…)}
 *       — omitted when the command carries no namespaces, exactly like Mongo's {@code ifPresentOrElse}.</li>
 *   <li>{@code $match} filter {@code CreateBsonVisitor.sudoApply(filterCriteria)} (Mongo:109-111) → the D2
 *       {@link CreateSqlVisitor#sudoApply(Criteria) sudo} predicate — <b>NO auth</b>, no global-read, no {@code delete_at}
 *       (operator metrics are system-level; Mongo uses {@code sudoApply}). The namespace term is AND-ed onto it (Mongo:114).</li>
 *   <li>{@code $group} {@code _id: {k: "$t." + path.replace('/','.')}} (Mongo:118-121) → each key is
 *       {@code (st.thing #>> <raw-segments>::text[])} projected as {@code g<i>}; the RAW (un-escaped) config segments
 *       address the {@code thing} JSONB exactly as Mongo's dotted {@code $t.attributes.x} does (jsonb {@code #>>} uses
 *       raw keys, NOT the flat-table RFC-6901 {@code wpath} encoding — see {@code AuthFilterSqlBuilder}).</li>
 *   <li>{@code Accumulators.sum("count", 1)} (Mongo:121) → {@code count(*) AS count}.</li>
 * </ul>
 * <p>
 * <b>Mongo {@code $group} zero-input parity (no-grouping metrics).</b> Mongo's {@code $group} emits ZERO documents
 * when the preceding {@code $match} selects zero input documents — there is nothing to group. A SQL aggregate
 * {@code count(*)} with NO {@code GROUP BY} does not share that behavior: it always returns exactly ONE row (a
 * {@code count} of {@code 0}), even over zero matching rows. A metric with an empty {@code groupingBy} therefore
 * renders as a bare {@code count(*)} (no {@code GROUP BY}, see {@link #assemble}), and that lone zero-count row must
 * be suppressed by the caller ({@link #isVanishedNoGroupRow}) so the Postgres backend emits the same zero-element
 * batch Mongo would. This matters beyond cosmetics: {@code OperatorAggregateMetricsProviderActor.reconcileVanishedBuckets}
 * treats an EMPTY aggregation batch as the signal that a previously-reported bucket has vanished and zeroes + removes
 * its Kamon gauge; a spurious {@code {"_id":{},"count":0}} element would be read as "still present, now zero" and the
 * stale gauge would never be reconciled away. Grouped metrics need no such treatment — a real {@code GROUP BY} groups
 * actual rows, so zero matching rows already yields zero groups, exactly like Mongo.
 * </p>
 * <p>
 * <b>Group-value type fidelity (verified from the sole consumer).</b> {@code AggregateThingsMetricsResponse.getGroupedBy}
 * reads the {@code _id} sub-object and calls {@code JsonValue.formatAsString()} on every value ({@code isString() ?
 * asString() : toString()}) — i.e. it stringifies, never type-discriminates; {@code getResult} reads {@code count} via
 * {@code asLong()}. Text extraction ({@code #>>}) is therefore correct (the brief's own criterion): a group key is
 * emitted as a JSON string, byte-identical to Mongo's {@code _id} sub-document for the (configured) string-valued keys,
 * and {@code count} is emitted as a JSON number (satisfying {@code asLong}). A missing key (SQL {@code NULL}) is emitted
 * as JSON {@code null}, mirroring Mongo's absent-field-&gt;{@code null} grouping. The only observable divergence — a
 * numeric/boolean group key rendered as a JSON string rather than a JSON number/boolean — is invisible to the consumer
 * (both stringify to the same text) and is documented + asserted in the tests.
 * </p>
 */
public final class PostgresAggregationQuery {

    private static final String DOC_TABLE = "search_things";
    private static final String DOC_ALIAS = "st";
    private static final String THING_COLUMN = "thing";
    private static final String NAMESPACE_COLUMN = "namespace";
    private static final String COUNT_ALIAS = "count";
    private static final SqlExpression COUNT_STAR = Sql.raw("count(*)");

    private PostgresAggregationQuery() {
        throw new AssertionError("no instances");
    }

    /**
     * One group-by key: the projected column {@link #alias() alias} ({@code g0}, {@code g1}, …), the {@link #outputKey()
     * output key} it is emitted under in the {@code _id} sub-object (the operator-metrics config key), and the RAW
     * (un-escaped) {@link #segments() JSONB path segments} into the {@code thing} document.
     */
    public record GroupKey(String alias, String outputKey, List<String> segments) {

        public GroupKey {
            Objects.requireNonNull(alias, "alias");
            Objects.requireNonNull(outputKey, "outputKey");
            segments = List.copyOf(segments);
        }
    }

    /**
     * Renders the operator-metrics {@code $match}+{@code $group} pipeline as parameterized SQL.
     *
     * @param filterCriteria the metric's RQL filter criteria (already parsed; {@code any()} for an absent filter).
     * @param sortedNamespaces the metric's namespaces (empty ⇒ no namespace {@code $match} term); pass a stable order so
     * the rendered SQL/binds are deterministic.
     * @param groupKeys the ordered group-by keys (empty ⇒ a single all-rows group, Mongo's {@code _id: {}}).
     * @return the rendered, parameterized statement.
     */
    public static RenderedSql assemble(final Criteria filterCriteria, final List<String> sortedNamespaces,
            final List<GroupKey> groupKeys) {
        final Select.Builder builder = Sql.select();
        for (final GroupKey key : groupKeys) {
            builder.column(
                    Sql.jsonbExtractText(Sql.col(DOC_ALIAS, THING_COLUMN), Sql.textArray(key.segments())),
                    key.alias());
        }
        builder.column(COUNT_STAR, COUNT_ALIAS);
        builder.from(DOC_TABLE, DOC_ALIAS);
        builder.where(where(filterCriteria, sortedNamespaces));
        // GROUP BY the projected group columns by ordinal (1-based); the trailing count(*) column is never grouped.
        for (int i = 0; i < groupKeys.size(); i++) {
            builder.groupBy(Sql.raw(Integer.toString(i + 1)));
        }
        return Sql.render(builder.build());
    }

    private static SqlExpression where(final Criteria filterCriteria, final List<String> sortedNamespaces) {
        final SqlExpression filter = CreateSqlVisitor.sudoApply(filterCriteria).predicate();
        if (sortedNamespaces.isEmpty()) {
            return filter;
        }
        final SqlExpression nsPredicate =
                Sql.eqAny(Sql.col(DOC_ALIAS, NAMESPACE_COLUMN), Sql.textArray(sortedNamespaces));
        return Sql.and(nsPredicate, filter);
    }

    /**
     * Whether a returned row is the spurious no-match artifact of a {@code GROUP BY}-less {@code count(*)} and must be
     * suppressed to preserve Mongo {@code $group} zero-input parity (see the class javadoc's
     * {@code reconcileVanishedBuckets} rationale). A {@code count(*)} with no {@code GROUP BY} always returns exactly
     * one row — even over zero matching rows, with {@code count == 0} — whereas Mongo's {@code $group} would emit no
     * documents at all for the same zero-match input.
     * <p>
     * Only applies when there are NO group keys: a grouped aggregation already returns zero rows on zero matches
     * (a real {@code GROUP BY} groups actual rows — no input rows means no groups), so this predicate must never
     * suppress a grouped row, even a defensively-checked zero-count one.
     * </p>
     *
     * @param groupKeys the metric's group-by keys, as rendered into the query.
     * @param count the row's {@code count(*)} value.
     * @return {@code true} if the row is the spurious no-match, no-grouping artifact and must be suppressed.
     */
    public static boolean isVanishedNoGroupRow(final List<GroupKey> groupKeys, final long count) {
        return groupKeys.isEmpty() && count == 0L;
    }

    /**
     * Maps a result row into the backend-neutral {@link JsonObject} the consumer parses — the SQL-side counterpart of
     * Mongo's {@code JsonFactory.newObject(document.toJson())}. Shape: {@code {"_id": {<key>: <value>, …}, "count": <n>}}
     * (empty {@code _id} object when there are no group keys, mirroring Mongo's {@code _id: {}}).
     *
     * @param groupKeys the ordered group-by keys.
     * @param textByAlias resolves each key's projected {@code g<i>} column to its extracted text ({@code null} for a
     * missing path / SQL {@code NULL}).
     * @param count the group's {@code count(*)}.
     * @return the emitted aggregation element.
     */
    public static JsonObject toAggregationJson(final List<GroupKey> groupKeys,
            final Function<String, String> textByAlias, final long count) {
        final JsonObjectBuilder id = JsonFactory.newObjectBuilder();
        for (final GroupKey key : groupKeys) {
            final String text = textByAlias.apply(key.alias());
            // #>> yields a JSON string for a present leaf; a missing leaf (SQL NULL) -> JSON null (Mongo absent-field parity).
            final JsonValue value = text == null ? JsonValue.nullLiteral() : JsonFactory.newValue(text);
            id.set(key.outputKey(), value);
        }
        return JsonFactory.newObjectBuilder()
                .set("_id", id.build())
                .set(COUNT_ALIAS, count)
                .build();
    }

    /**
     * Builds the ordered group-by keys from the metric's {@code grouping-by} config map (output-key ⇒ slash-path). The
     * entries are sorted by output key so the rendered SQL and the read-back column order are deterministic; each path is
     * split on {@code '/'} into RAW segments (leading empty segment dropped) — the faithful mirror of Mongo's
     * {@code path.replace("/", ".")} dotted addressing of {@code $t}.
     *
     * @param groupingBy the metric's {@code grouping-by} map (output key ⇒ {@code thing}-relative slash path).
     * @return the ordered group keys.
     */
    public static List<GroupKey> groupKeysOf(final java.util.Map<String, String> groupingBy) {
        final List<java.util.Map.Entry<String, String>> entries = new ArrayList<>(groupingBy.entrySet());
        entries.sort(java.util.Map.Entry.comparingByKey());
        final List<GroupKey> keys = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            final java.util.Map.Entry<String, String> entry = entries.get(i);
            keys.add(new GroupKey("g" + i, entry.getKey(), rawSegments(entry.getValue())));
        }
        return keys;
    }

    private static List<String> rawSegments(final String slashPath) {
        final List<String> segments = new ArrayList<>();
        for (final String segment : slashPath.split("/", -1)) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments;
    }

}
