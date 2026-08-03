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
package org.eclipse.ditto.timeseries.mongodb;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.bson.conversions.Bson;
import org.eclipse.ditto.rql.model.ParserException;
import org.eclipse.ditto.rql.model.predicates.ast.ExistsNode;
import org.eclipse.ditto.rql.model.predicates.ast.LogicalNode;
import org.eclipse.ditto.rql.model.predicates.ast.MultiComparisonNode;
import org.eclipse.ditto.rql.model.predicates.ast.Node;
import org.eclipse.ditto.rql.model.predicates.ast.RootNode;
import org.eclipse.ditto.rql.model.predicates.ast.SingleComparisonNode;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryInvalidException;

import com.mongodb.client.model.Filters;

/**
 * Translates an RQL predicate over ingest-time tags into a MongoDB filter on the time-series
 * {@code metaField}.
 * <p>
 * RQL is Ditto's query language — the same one {@code /api/2/search/things} accepts — so a cross-Thing
 * timeseries filter uses it too rather than inventing a second syntax. Field references are the tag
 * keys as declared in the WoT model, which are full Thing paths (e.g. {@code attributes/building}),
 * and each resolves to {@code meta.tags.<path>}.
 * <p>
 * Two properties of that mapping are worth stating, because they are what make it safe:
 * <ul>
 *   <li>A tag key is a Thing path, so it contains {@code /} and never {@code .}. It therefore cannot
 *   be misread by MongoDB as a nested field reference — which is exactly the trap a bare, unvalidated
 *   tag name would fall into.</li>
 *   <li>Only the {@code meta.tags} sub-document is addressable. A caller cannot reach
 *   {@code meta.thingId}, {@code meta.path} or the measurement value through the filter, so the
 *   filter cannot be used to sidestep the per-path authorization allow-list.</li>
 * </ul>
 * Tags are frozen on each data point at ingest, so this selects <em>points</em> by the state of the
 * world when they were recorded — not Things by their current state.
 *
 * @since 4.0.0
 */
final class TimeseriesRqlTranslator {

    private static final String TAG_FIELD_PREFIX =
            TimeseriesBsonMapper.FIELD_META + "." + TimeseriesBsonMapper.META_TAGS + ".";

    private TimeseriesRqlTranslator() {
        throw new AssertionError();
    }

    /**
     * Parses {@code rql} and translates it into a MongoDB filter.
     *
     * @param rql the RQL predicate, e.g. {@code and(eq(attributes/building,'A'),ge(attributes/floor,2))}.
     * @return the equivalent filter over {@code meta.tags.*}.
     * @throws TimeseriesQueryInvalidException if {@code rql} is unparseable or uses an unsupported
     * operator.
     */
    static Bson translate(final String rql) {
        return toFilter(parse(rql));
    }

    /**
     * Returns the tag keys (Thing paths) referenced by {@code rql}, so the caller can authorize them
     * before the filter is ever executed.
     *
     * @param rql the RQL predicate.
     * @return the referenced tag keys, in encounter order.
     * @throws TimeseriesQueryInvalidException if {@code rql} is unparseable.
     */
    static List<String> referencedFields(final String rql) {
        final List<String> fields = new ArrayList<>();
        collectFields(parse(rql), fields);
        return fields;
    }

    private static RootNode parse(final String rql) {
        try {
            return RqlPredicateParser.getInstance().parse(rql);
        } catch (final ParserException e) {
            throw TimeseriesQueryInvalidException
                    .newBuilder("The 'filter' is not a valid RQL predicate: " + e.getMessage())
                    .description("Use RQL as on /api/2/search/things, e.g. " +
                            "eq(attributes/building,'A') or and(eq(a,'x'),ge(b,2)).")
                    .build();
        }
    }

    private static void collectFields(final Node node, final List<String> into) {
        if (node instanceof SingleComparisonNode) {
            add(into, ((SingleComparisonNode) node).getComparisonProperty());
        } else if (node instanceof MultiComparisonNode) {
            add(into, ((MultiComparisonNode) node).getComparisonProperty());
        } else if (node instanceof ExistsNode) {
            add(into, ((ExistsNode) node).getProperty());
        } else if (node instanceof RootNode) {
            for (final Node child : ((RootNode) node).getChildren()) {
                collectFields(child, into);
            }
        } else if (node instanceof LogicalNode) {
            for (final Node child : ((LogicalNode) node).getChildren()) {
                collectFields(child, into);
            }
        }
    }

    private static void add(final List<String> into, final String field) {
        if (!into.contains(field)) {
            into.add(field);
        }
    }

    private static Bson toFilter(final Node node) {
        if (node instanceof RootNode) {
            final List<Node> children = ((RootNode) node).getChildren();
            if (children.isEmpty()) {
                // An empty predicate selects everything; represented as an empty $and so callers can
                // compose it unconditionally.
                return Filters.and(new ArrayList<>());
            }
            return children.size() == 1 ? toFilter(children.get(0)) : Filters.and(map(children));
        }
        if (node instanceof LogicalNode) {
            final LogicalNode logical = (LogicalNode) node;
            final List<Bson> children = map(logical.getChildren());
            switch (logical.getType()) {
                case AND:
                    return Filters.and(children);
                case OR:
                    return Filters.or(children);
                case NOT:
                    if (children.size() != 1) {
                        throw unsupported("not() takes exactly one argument");
                    }
                    return Filters.nor(children);
                default:
                    throw unsupported("logical operator <" + logical.getName() + ">");
            }
        }
        if (node instanceof SingleComparisonNode) {
            return single((SingleComparisonNode) node);
        }
        if (node instanceof MultiComparisonNode) {
            final MultiComparisonNode multi = (MultiComparisonNode) node;
            // IN is the only multi-comparison RQL defines.
            return Filters.in(tagField(multi.getComparisonProperty()), multi.getComparisonValue());
        }
        if (node instanceof ExistsNode) {
            return Filters.exists(tagField(((ExistsNode) node).getProperty()));
        }
        throw unsupported("expression <" + node.getClass().getSimpleName() + ">");
    }

    private static Bson single(final SingleComparisonNode node) {
        final String field = tagField(node.getComparisonProperty());
        final Object value = node.getComparisonValue();
        switch (node.getComparisonType()) {
            case EQ:
                return Filters.eq(field, value);
            case NE:
                return Filters.ne(field, value);
            case GT:
                return Filters.gt(field, value);
            case GE:
                return Filters.gte(field, value);
            case LT:
                return Filters.lt(field, value);
            case LE:
                return Filters.lte(field, value);
            case LIKE:
                return Filters.regex(field, likeToRegex(String.valueOf(value)));
            case ILIKE:
                return Filters.regex(field, likeToRegex(String.valueOf(value)),
                        // 'i' for case-insensitive, matching RQL's ilike semantics.
                        "i");
            default:
                throw unsupported("comparison operator <" + node.getComparisonType() + ">");
        }
    }

    /**
     * Converts RQL's {@code like} wildcards to a regex. {@code *} matches any run of characters and
     * {@code ?} a single one; everything else is quoted, so a value containing regex metacharacters
     * cannot smuggle a pattern in.
     */
    private static String likeToRegex(final String like) {
        final StringBuilder regex = new StringBuilder("^");
        final StringBuilder literal = new StringBuilder();
        for (int i = 0; i < like.length(); i++) {
            final char c = like.charAt(i);
            if (c == '*' || c == '?') {
                if (literal.length() > 0) {
                    regex.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                regex.append(c == '*' ? ".*" : ".");
            } else {
                literal.append(c);
            }
        }
        if (literal.length() > 0) {
            regex.append(Pattern.quote(literal.toString()));
        }
        return regex.append('$').toString();
    }

    private static List<Bson> map(final List<Node> nodes) {
        final List<Bson> filters = new ArrayList<>(nodes.size());
        for (final Node child : nodes) {
            filters.add(toFilter(child));
        }
        return filters;
    }

    /**
     * Maps an RQL field reference onto the stored tag. Leading slashes are tolerated so
     * {@code /attributes/building} and {@code attributes/building} address the same tag, matching how
     * Ditto treats pointer-ish field names elsewhere.
     */
    private static String tagField(final String property) {
        final String key = property.startsWith("/") ? property.substring(1) : property;
        if (key.isEmpty()) {
            throw unsupported("an empty field reference");
        }
        if (key.indexOf('.') >= 0 || key.indexOf('$') >= 0) {
            // Would be read by MongoDB as a nested path / operator rather than a tag key.
            throw unsupported("field reference <" + property + ">: '.' and '$' are not allowed in a tag key");
        }
        return TAG_FIELD_PREFIX + key;
    }

    private static TimeseriesQueryInvalidException unsupported(final String what) {
        return TimeseriesQueryInvalidException
                .newBuilder("The 'filter' uses " + what + ", which is not supported for timeseries tags.")
                .description("Supported: eq, ne, gt, ge, lt, le, in, like, ilike, exists, and, or, not " +
                        "over tag keys declared in the WoT model.")
                .build();
    }
}
