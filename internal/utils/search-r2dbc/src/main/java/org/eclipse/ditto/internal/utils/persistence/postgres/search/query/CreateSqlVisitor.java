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

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Sql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.SqlExpression;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.Predicate;
import org.eclipse.ditto.rql.query.criteria.visitors.CriteriaVisitor;
import org.eclipse.ditto.rql.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;

/**
 * The SQL counterpart of {@code CreateBsonVisitor} (thingsearch/service): translates a whole {@link Criteria} into a
 * {@link TranslatedCriteria} — the AST boolean predicate over {@code search_things st}.
 * <p>
 * <b>Side-by-side comment map (Mongo → here):</b>
 * <ul>
 *   <li>{@code apply:63-80} ({@code and(baseFilter, globalReadableFilter)}, global read AND-ed onto every non-sudo
 *       query) → {@link #apply}; {@code sudoApply:52-61} (no global-read, no deleteAt) → {@link #sudoApply}</li>
 *   <li>{@code visitField:92-100} (TimePlaceholder resolver at predicate creation) → {@link #visitField}</li>
 *   <li>{@code visitExists:82-85}/{@code visitEmpty:87-90} → {@link #visitExists}/{@link #visitEmpty}</li>
 *   <li>{@code visitAny:102-105} (match-all)/{@code visitAnd}/{@code visitOr}/{@code visitNor:107-120} → the boolean
 *       tree ({@code nor} = {@code NOT OR}, preserving Mongo nor semantics)</li>
 * </ul>
 */
public final class CreateSqlVisitor implements CriteriaVisitor<SqlExpression> {

    private static final TimePlaceholder TIME_PLACEHOLDER = TimePlaceholder.getInstance();

    @Nullable
    private final List<String> authorizationSubjectIds;

    private CreateSqlVisitor(@Nullable final List<String> authorizationSubjectIds) {
        this.authorizationSubjectIds = authorizationSubjectIds;
    }

    /**
     * Translates a criteria WITH visibility restriction (global-read AND-ed on — mirrors {@code CreateBsonVisitor.apply}).
     *
     * @param criteria the criteria to translate.
     * @param authorizationSubjectIds the subject IDs restricting visibility (must not be null).
     * @return the translated criteria.
     */
    public static TranslatedCriteria apply(final Criteria criteria, final List<String> authorizationSubjectIds) {
        final SqlExpression base = criteria.accept(new CreateSqlVisitor(List.copyOf(authorizationSubjectIds)));
        final SqlExpression globalRead = new AuthFilterSqlBuilder(authorizationSubjectIds).globalRead();
        // Both the per-field filter and the global-read term, so purely negated queries never leak invisible things.
        return new TranslatedCriteria(Sql.and(base, globalRead));
    }

    /**
     * Translates a criteria with NO visibility restriction (sudo — mirrors {@code CreateBsonVisitor.sudoApply}).
     *
     * @param criteria the criteria to translate.
     * @return the translated criteria.
     */
    public static TranslatedCriteria sudoApply(final Criteria criteria) {
        return new TranslatedCriteria(criteria.accept(new CreateSqlVisitor(null)));
    }

    @Override
    public SqlExpression visitAny() {
        return Sql.boolLiteral(true);
    }

    @Override
    public SqlExpression visitExists(final ExistsFieldExpression fieldExpression) {
        return GetExistsSqlVisitor.apply(fieldExpression, authorizationSubjectIds);
    }

    @Override
    public SqlExpression visitEmpty(final ExistsFieldExpression fieldExpression) {
        return GetEmptySqlVisitor.apply(fieldExpression, authorizationSubjectIds);
    }

    @Override
    public SqlExpression visitField(final FilterFieldExpression fieldExpression, final Predicate predicate) {
        final SqlPredicate sqlPredicate = predicate.accept(new CreateSqlPredicateVisitor(
                List.of(PlaceholderFactory.newPlaceholderResolver(TIME_PLACEHOLDER, new Object()))));
        return GetFilterSqlVisitor.apply(fieldExpression, sqlPredicate, authorizationSubjectIds);
    }

    @Override
    public SqlExpression visitAnd(final List<SqlExpression> conjuncts) {
        if (conjuncts.isEmpty()) {
            return Sql.boolLiteral(true);
        }
        return conjuncts.size() == 1 ? conjuncts.get(0) : Sql.and(conjuncts);
    }

    @Override
    public SqlExpression visitOr(final List<SqlExpression> disjoints) {
        if (disjoints.isEmpty()) {
            return Sql.boolLiteral(false);
        }
        return disjoints.size() == 1 ? disjoints.get(0) : Sql.or(disjoints);
    }

    @Override
    public SqlExpression visitNor(final List<SqlExpression> negativeDisjoints) {
        if (negativeDisjoints.isEmpty()) {
            // nor of nothing excludes nothing → match all (Mongo Filters.nor(empty)).
            return Sql.boolLiteral(true);
        }
        final SqlExpression or = negativeDisjoints.size() == 1
                ? negativeDisjoints.get(0)
                : Sql.or(negativeDisjoints);
        return Sql.not(or);
    }
}
