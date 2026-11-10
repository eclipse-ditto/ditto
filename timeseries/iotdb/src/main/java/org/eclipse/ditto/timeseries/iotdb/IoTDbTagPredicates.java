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
package org.eclipse.ditto.timeseries.iotdb;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.rql.model.ParserException;
import org.eclipse.ditto.rql.model.predicates.ast.ExistsNode;
import org.eclipse.ditto.rql.model.predicates.ast.LogicalNode;
import org.eclipse.ditto.rql.model.predicates.ast.MultiComparisonNode;
import org.eclipse.ditto.rql.model.predicates.ast.Node;
import org.eclipse.ditto.rql.model.predicates.ast.RootNode;
import org.eclipse.ditto.rql.model.predicates.ast.SingleComparisonNode;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.timeseries.iotdb.IoTDbSql.TagType;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryInvalidException;

/**
 * Translates tag filters into SQL predicates over the typed tag columns, with the semantics of the MongoDB
 * adapter: an RQL comparison only matches values of the literal's JSON type, while a {@code tagFilter} number
 * or boolean also matches its string form.
 * <p>
 * Every atomic predicate is two-valued ({@code col IS NOT NULL AND ...}), so {@code not()} and {@code ne} also
 * match points that lack the tag, as they do in MongoDB. A tag key without any column in the table has never
 * been ingested and is folded into a constant. Only tag columns are addressable, so a filter can never reach
 * {@code thing_id} or {@code path} and sidestep the per-path allow-list.
 */
@Immutable
final class IoTDbTagPredicates {

    static final String TRUE = "true";
    static final String FALSE = "false";

    private IoTDbTagPredicates() {
        throw new AssertionError();
    }

    /**
     * @param tagFilters exact-match tag filters of a single-Thing query, keyed by stored tag key.
     * @param columns the columns of the table.
     * @return the conjunction of the filters.
     * @throws TimeseriesQueryInvalidException if a filter value is an object or array.
     */
    static String forTagFilters(final Map<String, JsonValue> tagFilters, final Set<String> columns) {
        final List<String> clauses = new ArrayList<>(tagFilters.size());
        tagFilters.forEach((key, value) -> clauses.add(tagFilter(key, value, columns)));
        return and(clauses);
    }

    private static String tagFilter(final String key, final JsonValue value, final Set<String> columns) {
        if (value.isNull()) {
            return or(List.of(isNull(key, columns), eq(key, TagType.STRING, IoTDbSql.literal("null"), columns)));
        }
        if (value.isString()) {
            return eq(key, TagType.STRING, IoTDbSql.literal(value.asString()), columns);
        }
        final String asString = eq(key, TagType.STRING, IoTDbSql.literal(value.toString()), columns);
        if (value.isNumber()) {
            return or(List.of(eq(key, TagType.NUMBER, IoTDbSql.literal(value.asDouble()), columns), asString));
        }
        if (value.isBoolean()) {
            return or(List.of(eq(key, TagType.BOOLEAN, IoTDbSql.literal(value.asBoolean()), columns), asString));
        }
        throw TimeseriesQueryInvalidException.newBuilder("The tag filter <" + key +
                "> must be a string, number, boolean or null value.").build();
    }

    /**
     * @param rql an RQL predicate over tag keys, e.g. {@code and(eq(attributes/building,'A'),ge(attributes/floor,2))}.
     * @param columns the columns of the table.
     * @return the equivalent SQL predicate.
     * @throws TimeseriesQueryInvalidException if {@code rql} is unparseable or uses an unsupported construct.
     */
    static String forRql(final String rql, final Set<String> columns) {
        final RootNode root;
        try {
            root = RqlPredicateParser.getInstance().parse(rql);
        } catch (final ParserException e) {
            throw TimeseriesQueryInvalidException
                    .newBuilder("The 'filter' is not a valid RQL predicate: " + e.getMessage())
                    .description("Use RQL as on /api/2/search/things, e.g. " +
                            "eq(attributes/building,'A') or and(eq(a,'x'),ge(b,2)).")
                    .build();
        }
        return toSql(root, columns);
    }

    /**
     * Strips one leading slash, so {@code /attributes/building} and {@code attributes/building} address the
     * same tag, as in the RQL filter and {@code groupBy} of the MongoDB adapter.
     */
    static String normalizeTagKey(final String property) {
        final String key = property.startsWith("/") ? property.substring(1) : property;
        if (key.isEmpty()) {
            throw unsupported("an empty field reference");
        }
        return key;
    }

    private static String toSql(final Node node, final Set<String> columns) {
        if (node instanceof RootNode root) {
            return and(map(root.getChildren(), columns));
        }
        if (node instanceof LogicalNode logical) {
            final List<String> children = map(logical.getChildren(), columns);
            return switch (logical.getType()) {
                case AND -> and(children);
                case OR -> or(children);
                case NOT -> {
                    if (children.size() != 1) {
                        throw unsupported("not() with other than exactly one argument");
                    }
                    yield not(children.get(0));
                }
            };
        }
        if (node instanceof SingleComparisonNode single) {
            return single(single, columns);
        }
        if (node instanceof MultiComparisonNode multi) {
            final String key = normalizeTagKey(multi.getComparisonProperty());
            final List<String> alternatives = new ArrayList<>();
            for (final Object value : multi.getComparisonValue()) {
                alternatives.add(equalTo(key, value, columns));
            }
            return or(alternatives);
        }
        if (node instanceof ExistsNode exists) {
            return not(isNull(normalizeTagKey(exists.getProperty()), columns));
        }
        throw unsupported("expression <" + node.getClass().getSimpleName() + ">");
    }

    private static String single(final SingleComparisonNode node, final Set<String> columns) {
        final String key = normalizeTagKey(node.getComparisonProperty());
        final Object value = node.getComparisonValue();
        return switch (node.getComparisonType()) {
            case EQ -> equalTo(key, value, columns);
            case NE -> not(equalTo(key, value, columns));
            case GT -> range(key, ">", value, columns);
            case GE -> range(key, ">=", value, columns);
            case LT -> range(key, "<", value, columns);
            case LE -> range(key, "<=", value, columns);
            case LIKE -> like(key, value, false, columns);
            case ILIKE -> like(key, value, true, columns);
        };
    }

    private static String equalTo(final String key, @Nullable final Object value, final Set<String> columns) {
        if (value == null) {
            return isNull(key, columns);
        }
        return compare(key, "=", value, columns);
    }

    private static String range(final String key, final String operator, @Nullable final Object value,
            final Set<String> columns) {

        if (value == null || value instanceof Boolean) {
            throw unsupported("<" + operator + "> with a " + (value == null ? "null" : "boolean") + " value");
        }
        return compare(key, operator, value, columns);
    }

    private static String compare(final String key, final String operator, final Object value,
            final Set<String> columns) {

        if (value instanceof String string) {
            return predicate(key, TagType.STRING, operator + " " + IoTDbSql.literal(string), columns);
        }
        if (value instanceof Number number) {
            final double d = number.doubleValue();
            if (!Double.isFinite(d)) {
                throw unsupported("a non-finite number");
            }
            return predicate(key, TagType.NUMBER, operator + " " + IoTDbSql.literal(d), columns);
        }
        if (value instanceof Boolean bool) {
            return predicate(key, TagType.BOOLEAN, operator + " " + IoTDbSql.literal(bool), columns);
        }
        throw unsupported("the value <" + value + ">");
    }

    private static String like(final String key, @Nullable final Object value, final boolean ignoreCase,
            final Set<String> columns) {

        if (!(value instanceof String pattern)) {
            throw unsupported("like/ilike with a non-string pattern");
        }
        final String column = IoTDbSql.tagColumn(key, TagType.STRING);
        if (!columns.contains(column)) {
            return FALSE;
        }
        final String sqlPattern = IoTDbSql.likePattern(ignoreCase ? pattern.toLowerCase(Locale.ROOT) : pattern);
        final String operand = ignoreCase ? "lower(" + IoTDbSql.quote(column) + ")" : IoTDbSql.quote(column);
        return "(" + IoTDbSql.quote(column) + " IS NOT NULL AND " + operand + " LIKE " +
                IoTDbSql.literal(sqlPattern) + " ESCAPE '\\')";
    }

    private static String eq(final String key, final TagType type, final String literal, final Set<String> columns) {
        return predicate(key, type, "= " + literal, columns);
    }

    private static String predicate(final String key, final TagType type, final String comparison,
            final Set<String> columns) {

        final String column = IoTDbSql.tagColumn(key, type);
        if (!columns.contains(column)) {
            return FALSE;
        }
        final String quoted = IoTDbSql.quote(column);
        return "(" + quoted + " IS NOT NULL AND " + quoted + " " + comparison + ")";
    }

    private static String isNull(final String key, final Set<String> columns) {
        final List<String> clauses = new ArrayList<>(3);
        for (final TagType type : TagType.values()) {
            final String column = IoTDbSql.tagColumn(key, type);
            if (columns.contains(column)) {
                clauses.add(IoTDbSql.quote(column) + " IS NULL");
            }
        }
        return and(clauses);
    }

    private static List<String> map(final List<Node> nodes, final Set<String> columns) {
        final List<String> out = new ArrayList<>(nodes.size());
        for (final Node node : nodes) {
            out.add(toSql(node, columns));
        }
        return out;
    }

    static String and(final List<String> clauses) {
        return join(clauses, " AND ", TRUE, FALSE);
    }

    static String or(final List<String> clauses) {
        return join(clauses, " OR ", FALSE, TRUE);
    }

    static String not(final String clause) {
        if (TRUE.equals(clause)) {
            return FALSE;
        }
        if (FALSE.equals(clause)) {
            return TRUE;
        }
        return "(NOT " + clause + ")";
    }

    private static String join(final List<String> clauses, final String separator, final String identity,
            final String absorbing) {

        final List<String> kept = new ArrayList<>(clauses.size());
        for (final String clause : clauses) {
            if (absorbing.equals(clause)) {
                return absorbing;
            }
            if (!identity.equals(clause)) {
                kept.add(clause);
            }
        }
        if (kept.isEmpty()) {
            return identity;
        }
        return kept.size() == 1 ? kept.get(0) : "(" + String.join(separator, kept) + ")";
    }

    private static TimeseriesQueryInvalidException unsupported(final String what) {
        return TimeseriesQueryInvalidException
                .newBuilder("The 'filter' uses " + what + ", which is not supported for timeseries tags.")
                .description("Supported: eq, ne, gt, ge, lt, le, in, like, ilike, exists, and, or, not " +
                        "over tag keys declared in the WoT model.")
                .build();
    }
}
