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
 * The output of the RQL→SQL criteria translation ({@link CreateSqlVisitor}), consumed by the sort/paging translation
 * ({@link PostgresSortClause}) and the statement assembly ({@link SearchQueryAssembler}): the translated RQL criteria
 * as an AST boolean {@link #predicate() expression} over {@code search_things st} (with {@code EXISTS} probes on
 * {@code search_flat} and auth rechecks on the doc-row JSONB columns), plus a {@link #materializedCtePrelude() CTE
 * prelude} seam.
 * <p>
 * <b>Kept deliberately minimal.</b> The prelude is an empty seam: the criteria translation never fills it, so it is
 * always empty. The selective-leg CTE ({@code WITH … AS MATERIALIZED}) belongs to the top-level statement, not to the
 * per-predicate translation: {@link SearchQueryAssembler} adds it at statement assembly through
 * {@link SelectiveLegCte}, which also decides which conjunct to materialise.
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
     * @return the list of materialized CTEs to prepend to the query; always empty, because the criteria translation
     * never fills it (the selective-leg CTE is added by {@link SearchQueryAssembler} through {@link SelectiveLegCte}).
     */
    public List<Select> materializedCtePrelude() {
        return materializedCtePrelude;
    }
}
