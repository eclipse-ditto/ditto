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

import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.SqlExpression;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.rql.query.expression.SortFieldExpression;
import org.eclipse.ditto.rql.query.expression.visitors.SortFieldExpressionVisitor;

/**
 * The SQL counterpart of {@code GetSortBsonVisitor} (thingsearch/service): resolves a {@code SortFieldExpression} into
 * the {@link SortField} the {@link PostgresSortClause} turns into a lateral + {@code ORDER BY}. Like the Mongo sort
 * visitor it carries <b>NO auth</b> at all (plan §3.3 exception 3 — sort keys are never authorization-filtered) and it
 * resolves the same paths {@code GetSortBsonVisitor} does.
 * <p>
 * <b>Side-by-side comment map (Mongo → here):</b>
 * <ul>
 *   <li>{@code visitAttribute:99-102} → {@link #visitAttribute}</li>
 *   <li>{@code visitFeatureDefinition:105-108}/{@code visitFeatureIdProperty:111-115}/
 *       {@code visitFeatureIdDesiredProperty:118-123} → the three feature methods (wildcard {@code *} REJECTED — see
 *       below)</li>
 *   <li>{@code visitSimple:126-130} (slash-prefixed ⇒ flat probe, else the raw internal name ⇒ doc column) →
 *       {@link #visitSimple}</li>
 * </ul>
 * <b>Wildcard rejection (plan §3.5):</b> Mongo cannot sort on {@code features/*} wildcard paths — {@code
 * GetSortBsonVisitor} has no wildcard branch, so a wildcard feature id becomes a literal {@code *} path segment that
 * matches no document (a degenerate, order-less "sort"). On the Postgres flat table the {@code /features/*} wpath is a
 * REAL row family (the flattener emits it), so silently building a lateral over it would sort by an aggregate across
 * every feature — a divergence from Mongo, not a match. To keep parity this visitor rejects wildcard feature sort keys
 * outright with an {@link IllegalArgumentException} (the same exception family {@code ThingsFieldExpressionFactory}
 * raises for an unmappable sort field), so a wildcard sort fails loudly instead of diverging.
 * <p>
 * See {@link PostgresSortClause}'s class javadoc ("Documented divergences vs MongoDB sort") for this rejection
 * alongside the other two known, intentional Postgres/Mongo sort divergences (object-valued sort-key ties;
 * array-of-array ASC/DESC asymmetry) — that is the single consolidated list; it is not duplicated here.
 * </p>
 */
final class GetSortSqlVisitor implements SortFieldExpressionVisitor<SortField> {

    private static final String FEATURE_ID_WILDCARD = "*";

    private GetSortSqlVisitor() {}

    /**
     * @param expression the RQL sort field expression.
     * @return the resolved {@link SortField}.
     * @throws IllegalArgumentException if the expression is a wildcard-feature sort path (unsupported, parity with
     * Mongo), or a root-mapped field name that is not one of the two sortable system columns.
     */
    static SortField apply(final SortFieldExpression expression) {
        return expression.acceptSortVisitor(new GetSortSqlVisitor());
    }

    @Override
    public SortField visitAttribute(final String key) {
        return SortField.ofFlatPath(AbstractFieldSqlCreator.wpathOf(JsonPointer.of("/attributes/" + key)));
    }

    @Override
    public SortField visitFeatureDefinition(final String featureId) {
        rejectWildcard(featureId);
        return SortField.ofFlatPath(AbstractFieldSqlCreator.wpathOf(
                JsonPointer.of("/features/" + featureId + "/definition")));
    }

    @Override
    public SortField visitFeatureIdProperty(final String featureId, final String property) {
        rejectWildcard(featureId);
        return SortField.ofFlatPath(AbstractFieldSqlCreator.wpathOf(
                JsonPointer.of("/features/" + featureId + "/properties/" + property)));
    }

    @Override
    public SortField visitFeatureIdDesiredProperty(final CharSequence featureId, final CharSequence desiredProperty) {
        rejectWildcard(featureId);
        return SortField.ofFlatPath(AbstractFieldSqlCreator.wpathOf(
                JsonPointer.of("/features/" + featureId + "/desiredProperties/" + desiredProperty)));
    }

    @Override
    public SortField visitSimple(final String fieldName) {
        if (fieldName.startsWith("/")) {
            // Slash-mapped simple field (e.g. /_modified, /policyId): a flat probe, sorted from the lateral.
            return SortField.ofFlatPath(AbstractFieldSqlCreator.wpathOf(JsonPointer.of(fieldName)));
        }
        // Root-mapped system field (_id → st.thing_id, _namespace → st.namespace): a direct doc column, no lateral.
        final SqlExpression column = AbstractFieldSqlCreator.rootColumn(fieldName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown root-level sort field: " + fieldName));
        return SortField.ofRootColumn(column);
    }

    private static void rejectWildcard(final CharSequence featureId) {
        if (FEATURE_ID_WILDCARD.contentEquals(featureId)) {
            throw new IllegalArgumentException("Sorting on wildcard feature paths (features/*/...) is not supported "
                    + "— parity with MongoDB, whose sort visitor has no wildcard branch (plan §3.5).");
        }
    }
}
