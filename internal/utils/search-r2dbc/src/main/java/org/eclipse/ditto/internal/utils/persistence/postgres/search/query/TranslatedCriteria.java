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

import java.util.List;
import java.util.Objects;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Select;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.SqlExpression;

/**
 * The D2 output contract consumed by D3 (sort/cursor) and D4 (persistence): the translated RQL criteria as an AST
 * boolean {@link #predicate() expression} over {@code search_things st} (with {@code EXISTS} probes on
 * {@code search_flat} and auth rechecks on the doc-row JSONB columns), plus a {@link #materializedCtePrelude() CTE
 * prelude} seam.
 * <p>
 * <b>Kept deliberately minimal — D3/D4 will extend it.</b> The 0.2b materialized selective-leg "rescue" CTE strategy
 * applies to the TOP-LEVEL query assembly (D4), NOT inside D2's per-predicate translation; the prelude list is the seam
 * where that assembly can hoist a selective leg into a {@code WITH … AS MATERIALIZED} CTE and reference it from
 * {@link #predicate()}. D2 itself always produces an empty prelude (no CTEs); the selectivity-ordering decision is a D4
 * concern.
 * </p>
 */
public final class TranslatedCriteria {

    private final SqlExpression predicate;
    private final List<Select> materializedCtePrelude;

    TranslatedCriteria(final SqlExpression predicate) {
        this(predicate, List.of());
    }

    TranslatedCriteria(final SqlExpression predicate, final List<Select> materializedCtePrelude) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.materializedCtePrelude = List.copyOf(materializedCtePrelude);
    }

    /**
     * @return the AST boolean expression to place in the query's {@code WHERE} clause (already including the global-read
     * term for {@code apply()}, or without it for {@code sudoApply()}).
     */
    public SqlExpression predicate() {
        return predicate;
    }

    /**
     * @return the (currently always empty) list of materialized CTEs to prepend to the query — the D4 selective-leg
     * "rescue" seam (§3.5).
     */
    public List<Select> materializedCtePrelude() {
        return materializedCtePrelude;
    }
}
