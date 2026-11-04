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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.JsonPointerSegments;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument;

/**
 * Flattens a thing payload into {@code search_flat} rows (plan §3.2 amended, Task C1 adjudications). This is the
 * production counterpart of the Phase-0 bench {@code Flattener} spike; it operates directly on Ditto's
 * {@link JsonObject}/{@link JsonArray}/{@link JsonValue} tree instead of the bench's synthetic {@code Map}/
 * {@code List} corpus fixtures, since that is the shape {@link SearchIndexDocument#thing()} actually has.
 * <p>
 * <b>Source of truth:</b> flattening reads {@link SearchIndexDocument#thing()} ONLY. It never reads
 * {@link SearchIndexDocument.FeatureEntry#content()} — see that field's contract javadoc: {@code content()} is not
 * length-enforced and exists solely for the Mongo encoder's legacy {@code f} array. {@link #flatten(SearchIndexDocument)}
 * enforces this structurally: it is implemented purely in terms of {@link #flatten(String, JsonObject)} applied to
 * {@code document.thing()}, so it can never observe {@code policyAuth()}, {@code globalRead()}, or
 * {@code features()[].auth()}/{@code content()} at all.
 * </p>
 * <p>
 * <b>RFC-6901 path escaping (binding, closes a plan gap):</b> every JSON object key is escaped per RFC 6901 before
 * being joined into {@link FlatRow#path()}/{@link FlatRow#wpath()} with {@code '/'}: a literal {@code '~'} becomes
 * {@code "~0"} and a literal {@code '/'} becomes {@code "~1"}, escaping {@code '~'} FIRST so a {@code '/'} escape
 * never gets re-processed into a spurious tilde-escape. This is necessary because {@code path}/{@code wpath} are
 * flat SQL {@code TEXT} columns with {@code '/'} as the only segment separator — unlike Ditto's own
 * {@link org.eclipse.ditto.json.JsonPointer} Java API, which sidesteps the slash ambiguity via the
 * {@link org.eclipse.ditto.json.JsonKey} type distinction and therefore only documents tilde-escaping. The escaping
 * is exposed as {@link #escapeJsonPointerSegment(String)} so that the future Phase-D RQL→SQL translator can (and
 * MUST) apply the byte-for-byte identical transform to query-path segments before comparing against these columns —
 * see plan Phase H.
 * </p>
 * <p>
 * <b>Dual-wpath on every feature-subtree row:</b> every row rooted anywhere under {@code /features/<id>/...}
 * (Task-0.1 adjudication) — scalar leaves AND object/empty-container exists-rows alike — is emitted TWICE: once
 * with {@code wpath = path} (the exact, feature-scoped address) and once with {@code wpath} rewritten to the
 * {@code /features/*}-wildcarded form (the cross-feature address), both carrying {@code fId = <id>}. Rows outside a
 * feature subtree carry {@code fId = null} and a single row with {@code wpath = path}.
 * </p>
 * <p>
 * <b>{@code ord}:</b> a row-enumeration counter per {@code (path, wpath)} within one thing — NOT an array index. It
 * is enumerated once per {@code path} (a superset key of {@code (path, wpath)}, since {@code wpath} is a
 * deterministic function of {@code path}) and only advances past 0 when array explosion produces more than one
 * occurrence at the same path; both physical rows of a single dual-wpath emission share that one {@code ord} value.
 * </p>
 * <p>
 * <b>Arrays</b> are exploded recursively through object/array nesting without appending an index segment to the
 * path (an array of objects reuses the array's own path for every element; repeat visits bump {@code ord}). The
 * ONLY exception (Mongo parity): when an array element is itself an array, that inner array is NOT exploded — it
 * contributes a single {@code typeRank = 5} (array) stub row with no value, instead of being walked into, at
 * whatever nesting depth that direct array-of-array occurs.
 * </p>
 * <p>
 * <b>Objects</b> always contribute an exists row ({@code typeRank = 4}, no value) at their own path, whether or not
 * they have children — so an empty object still gets exactly one row (two, if inside a feature subtree). Non-nested,
 * non-empty arrays do NOT get a container row (their exploded elements carry the semantic content); an empty array
 * gets a single {@code typeRank = 5} stub row so it isn't silently dropped.
 * </p>
 * <p>
 * <b>Numbers</b> are converted to {@link BigDecimal} without precision loss: a value that fits exactly in a
 * {@code long} (which subsumes every {@code int}) is converted via {@link JsonValue#asLong()}; only a value that
 * does not fit in a {@code long} (i.e. has a fractional part, or is out of {@code long} range) falls through to
 * {@link JsonValue#asDouble()}. Converting every number's {@code double} value first would silently round longs
 * beyond {@code 2^53} (Ditto numbers are {@code int}/{@code long}/{@code double} only, per plan §3.2).
 * </p>
 * <p>
 * <b>Emission order</b> is deterministic (a pre-order walk in {@link JsonObject}'s own field iteration order,
 * repeatable across calls on the same input) but not sorted; Task C2's bulk-write path may rely on that
 * repeatability for stable column-wise extraction, but must not assume any particular sort order.
 * </p>
 *
 * @since 4.0.0
 */
@Immutable
public final class ThingFlattener {

    public static final short TYPE_NULL = 1;
    public static final short TYPE_NUMBER = 2;
    public static final short TYPE_STRING = 3;
    public static final short TYPE_OBJECT = 4;
    public static final short TYPE_ARRAY = 5;
    public static final short TYPE_BOOLEAN = 6;

    private static final String FEATURES_ROOT = "/features";

    private ThingFlattener() {
        throw new AssertionError("no instances");
    }

    /**
     * Flattens {@code document.thing()} — and ONLY that field of the document — into {@code search_flat} rows.
     *
     * @param document the neutral search-index document; only {@link SearchIndexDocument#thing()} and
     * {@link SearchIndexDocument#thingId()} are read.
     * @return the flattened rows, in a stable but unspecified (not sorted) order.
     */
    public static List<FlatRow> flatten(final SearchIndexDocument document) {
        Objects.requireNonNull(document, "document");
        return flatten(document.thingId().toString(), document.thing());
    }

    /**
     * Flattens {@code thing}'s children (the root object itself does not get a row — only its top-level keys and
     * everything below them do).
     *
     * @param thingId the owning thing's ID, copied verbatim into every emitted row.
     * @param thing the thing payload; an object whose values are recursively string/number/boolean/null/object/array.
     * @return the flattened rows, in a stable but unspecified (not sorted) order.
     */
    public static List<FlatRow> flatten(final String thingId, final JsonObject thing) {
        Objects.requireNonNull(thingId, "thingId");
        Objects.requireNonNull(thing, "thing");

        final List<FlatRow> rows = new ArrayList<>();
        final Map<String, Integer> ordCounters = new HashMap<>();
        for (final JsonField field : thing) {
            final String childPath = "/" + escapeJsonPointerSegment(field.getKeyName());
            walk(rows, ordCounters, thingId, field.getValue(), childPath, null);
        }
        return rows;
    }

    /**
     * Escapes a single (unescaped) JSON object key into an RFC-6901 path segment: {@code '~'} to {@code "~0"}
     * (checked first) and {@code '/'} to {@code "~1"} (checked second), in one left-to-right pass so a slash's
     * escape sequence is never subsequently re-escaped.
     * <p>
     * Exposed so the Phase-D RQL→SQL translator can apply the identical transform to query-path segments — the two
     * MUST stay in lockstep, since {@code path}/{@code wpath} in {@code search_flat} are produced by this method.
     * </p>
     *
     * @param rawKey the raw (unescaped) JSON object key.
     * @return the RFC-6901-escaped path segment.
     */
    public static String escapeJsonPointerSegment(final String rawKey) {
        return JsonPointerSegments.escape(rawKey);
    }

    private static void walk(final List<FlatRow> rows, final Map<String, Integer> ordCounters, final String thingId,
            final JsonValue node, final String path, @Nullable final FeatureContext feature) {

        if (node.isNull()) {
            emit(rows, ordCounters, thingId, path, feature, TYPE_NULL, null, null, null);
        } else if (node.isObject()) {
            emit(rows, ordCounters, thingId, path, feature, TYPE_OBJECT, null, null, null);
            final JsonObject objectNode = node.asObject();
            for (final JsonField field : objectNode) {
                final String childPath = path + "/" + escapeJsonPointerSegment(field.getKeyName());
                final FeatureContext childFeature = feature == null && FEATURES_ROOT.equals(path)
                        ? new FeatureContext(field.getKeyName(), childPath)
                        : feature;
                walk(rows, ordCounters, thingId, field.getValue(), childPath, childFeature);
            }
        } else if (node.isArray()) {
            final JsonArray arrayNode = node.asArray();
            if (arrayNode.isEmpty()) {
                emit(rows, ordCounters, thingId, path, feature, TYPE_ARRAY, null, null, null);
            } else {
                for (final JsonValue element : arrayNode) {
                    if (element.isArray()) {
                        // Direct array-of-array: the inner array is NOT exploded (Mongo parity) -- one stub row
                        // per outer element, no descent into the inner array's own content, at whatever depth this
                        // occurs.
                        emit(rows, ordCounters, thingId, path, feature, TYPE_ARRAY, null, null, null);
                    } else {
                        // Same path for every element -- no index segment -- so repeat scalars/objects at this
                        // path bump `ord` rather than collide.
                        walk(rows, ordCounters, thingId, element, path, feature);
                    }
                }
            }
        } else if (node.isString()) {
            emit(rows, ordCounters, thingId, path, feature, TYPE_STRING, null, null, node.asString());
        } else if (node.isBoolean()) {
            emit(rows, ordCounters, thingId, path, feature, TYPE_BOOLEAN, node.asBoolean(), null, null);
        } else if (node.isNumber()) {
            emit(rows, ordCounters, thingId, path, feature, TYPE_NUMBER, null, toBigDecimal(node), null);
        } else {
            throw new IllegalArgumentException("Unsupported JSON value node: " + node);
        }
    }

    private static void emit(final List<FlatRow> rows, final Map<String, Integer> ordCounters, final String thingId,
            final String path, @Nullable final FeatureContext feature, final short typeRank,
            @Nullable final Boolean valBool, @Nullable final BigDecimal valNum, @Nullable final String valText) {

        // Enumerated per `path` alone (a superset key of `(path, wpath)`, since wpath is a deterministic function
        // of path): both physical rows of a dual-wpath emission below share this single ord.
        final int ord = ordCounters.merge(path, 1, Integer::sum) - 1;

        if (feature == null) {
            rows.add(new FlatRow(thingId, path, path, null, ord, typeRank, valBool, valNum, valText));
        } else {
            rows.add(new FlatRow(thingId, path, path, feature.featureId(), ord, typeRank, valBool, valNum, valText));
            final String starWpath =
                    FEATURES_ROOT + "/*" + path.substring(feature.featureSubtreeRootPath().length());
            rows.add(new FlatRow(thingId, path, starWpath, feature.featureId(), ord, typeRank, valBool, valNum,
                    valText));
        }
    }

    private static BigDecimal toBigDecimal(final JsonValue number) {
        if (number.isLong()) {
            return BigDecimal.valueOf(number.asLong());
        }
        return BigDecimal.valueOf(number.asDouble());
    }

    /**
     * The feature subtree a node is nested under, captured once at the point a {@code /features} child is entered.
     *
     * @param featureId the raw (unescaped) feature ID, exposed verbatim on {@link FlatRow#fId()}.
     * @param featureSubtreeRootPath the already-escaped {@code "/features/<escapedFeatureKey>"} path prefix, as it
     * literally appears at the start of every path nested under this feature. Capturing the exact string (rather
     * than recomputing it from {@code featureId}) is what keeps the {@code /features/*} wildcard rewrite correct
     * even when the feature ID itself contains RFC-6901 special characters.
     */
    private record FeatureContext(String featureId, String featureSubtreeRootPath) {
    }

}
