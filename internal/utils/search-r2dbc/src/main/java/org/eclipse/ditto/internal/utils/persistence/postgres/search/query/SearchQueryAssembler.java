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

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.RenderedSql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Select;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Sql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.SqlExpression;
import org.eclipse.ditto.rql.query.criteria.Criteria;

/**
 * Assembles the Task&nbsp;D4 read statements — the {@code findAll} / {@code findAllUnlimited} row queries and the
 * {@code count} — from a translated {@link Criteria}, a {@link PostgresSortClause} (D3) and the
 * {@link SelectiveLegCte selective-leg CTE} (§3.5; which conjunct it materialises: see {@link SelectiveLegCte} and
 * {@link SelectiveLegs}). Pure and DB-free: it returns rendered, fully-parameterized {@link RenderedSql} so the exact
 * SQL and bind order are unit-testable without a database.
 * <p>
 * <b>Statement shape ({@code findAll}).</b>
 * <pre>{@code
 * [WITH sel_leg AS MATERIALIZED (SELECT sfc.thing_id FROM search_flat sfc WHERE …)]       -- a point leg alone, or
 * [WITH sel_pick AS MATERIALIZED (…), sel_leg AS MATERIALIZED (SELECT sli.thing_id FROM (…) sli GROUP BY sli.thing_id)]
 * SELECT st.thing_id, st.t_modified, <cursor-projection cols>
 * FROM search_things st
 * [LEFT JOIN LATERAL (…) k0 ON true …]                              -- one per flat sort key (D3)
 * WHERE [EXISTS (SELECT 1 FROM sel_leg sl WHERE sl.thing_id = st.thing_id) AND] <full translated predicate>
 * ORDER BY … LIMIT $ OFFSET $
 * }</pre>
 * The selective-leg semi-join is an <em>additional</em> conjunct; the full predicate (auth rechecks included) is always
 * applied, so the CTE only ever changes the plan, never the result (see {@link SelectiveLegCte}). A two-sided range
 * alone gets no CTE (see {@link SelectiveLegCte}). Reads do NOT filter
 * {@code delete_at} (plan §3.6): a purge-marked row stays visible until the reaper removes it, matching Mongo (whose
 * TTL-marked docs likewise remain queryable until physically deleted).
 */
public final class SearchQueryAssembler {

    private static final String DOC_ALIAS = AbstractFieldSqlCreator.DOC_ALIAS;
    private static final String FLAT_TABLE = AbstractFieldSqlCreator.FLAT_TABLE;
    private static final String THING_ID = AbstractFieldSqlCreator.THING_ID;
    private static final String DOC_TABLE = "search_things";
    private static final String T_MODIFIED = "t_modified";

    private static final String SEL_ALIAS = "sl";
    private static final String COUNT_CTE = "matched";
    private static final SqlExpression COUNT_STAR = Sql.raw("count(*)");
    private static final SqlExpression ONE = Sql.raw("1");

    private SearchQueryAssembler() {
        throw new AssertionError("no instances");
    }

    /**
     * {@link #findAll(Criteria, java.util.List, PostgresSortClause, long, long, SelectiveLegs)} with point legs only
     * ({@link SelectiveLegs#POINT}).
     *
     * @param criteria the query criteria.
     * @param authorizationSubjectIds the visibility subjects, or {@code null} for the sudo (no-auth) form.
     * @param sortClause the D3 sort/paging translation (the SAME instance the persistence uses for cursor extraction).
     * @param limit the row limit ({@code pageLimit + 1}).
     * @param offset the skip.
     * @return the rendered, parameterized statement.
     */
    public static RenderedSql findAll(final Criteria criteria, @Nullable final java.util.List<String> authorizationSubjectIds,
            final PostgresSortClause sortClause, final long limit, final long offset) {
        return findAll(criteria, authorizationSubjectIds, sortClause, limit, offset, SelectiveLegs.POINT);
    }

    /**
     * The {@code findAll} row query — thing-id + modified timestamp + the D3 cursor-projection columns, ordered and
     * paged. The caller passes {@code limit} as {@code pageLimit + 1} (the extra row is the "has-next-page" probe,
     * mirroring Mongo's {@code limit + 1}).
     *
     * @param criteria the query criteria.
     * @param authorizationSubjectIds the visibility subjects, or {@code null} for the sudo (no-auth) form.
     * @param sortClause the D3 sort/paging translation (the SAME instance the persistence uses for cursor extraction).
     * @param limit the row limit ({@code pageLimit + 1}).
     * @param offset the skip.
     * @param selectiveLegs which legs the selective-leg CTE may materialise.
     * @return the rendered, parameterized statement.
     */
    public static RenderedSql findAll(final Criteria criteria, @Nullable final java.util.List<String> authorizationSubjectIds,
            final PostgresSortClause sortClause, final long limit, final long offset,
            final SelectiveLegs selectiveLegs) {
        final Select.Builder builder = Sql.select()
                .column(Sql.col(DOC_ALIAS, THING_ID))
                .column(Sql.col(DOC_ALIAS, T_MODIFIED))
                .from(DOC_TABLE, DOC_ALIAS);
        applyWhereWithOptionalCte(builder, criteria, authorizationSubjectIds, selectiveLegs);
        sortClause.applyOrdering(builder);
        sortClause.applyCursorProjections(builder);
        builder.limit(Sql.bigint(limit)).offset(Sql.bigint(offset));
        return Sql.render(builder.build());
    }

    /**
     * {@link #findAllUnlimited(Criteria, java.util.List, PostgresSortClause, Long, long, SelectiveLegs)} with point legs
     * only ({@link SelectiveLegs#POINT}).
     *
     * @param criteria the query criteria.
     * @param authorizationSubjectIds the visibility subjects (never {@code null} on this path — parity with Mongo).
     * @param sortClause the D3 sort/paging translation.
     * @param limit the row limit, or {@code null} for unbounded.
     * @param offset the skip.
     * @return the rendered, parameterized statement.
     */
    public static RenderedSql findAllUnlimited(final Criteria criteria,
            @Nullable final java.util.List<String> authorizationSubjectIds, final PostgresSortClause sortClause,
            @Nullable final Long limit, final long offset) {
        return findAllUnlimited(criteria, authorizationSubjectIds, sortClause, limit, offset, SelectiveLegs.POINT);
    }

    /**
     * The {@code findAllUnlimited} streaming query — thing-id only, ordered, optionally limited and skipped
     * ({@code null} limit ⇒ unbounded; any non-null {@code limit} — including {@code 0} — is rendered as a literal
     * SQL {@code LIMIT}). The caller, {@code PostgresThingsSearchPersistence.findAllUnlimited} (via its
     * {@code toRowLimit} helper), is responsible for normalizing the upstream {@link org.eclipse.ditto.rql.query.Query#getLimit()}
     * 0-sentinel — the Mongo-transcribed {@code cursor.limit(0)} "no limit" default — to {@code null} <em>before</em>
     * calling this method; unlike MongoDB, SQL {@code LIMIT 0} means zero rows, not unbounded.
     *
     * @param criteria the query criteria.
     * @param authorizationSubjectIds the visibility subjects (never {@code null} on this path — parity with Mongo).
     * @param sortClause the D3 sort/paging translation.
     * @param limit the row limit, or {@code null} for unbounded.
     * @param offset the skip.
     * @param selectiveLegs which legs the selective-leg CTE may materialise.
     * @return the rendered, parameterized statement.
     */
    public static RenderedSql findAllUnlimited(final Criteria criteria,
            @Nullable final java.util.List<String> authorizationSubjectIds, final PostgresSortClause sortClause,
            @Nullable final Long limit, final long offset, final SelectiveLegs selectiveLegs) {
        final Select.Builder builder = Sql.select()
                .column(Sql.col(DOC_ALIAS, THING_ID))
                .from(DOC_TABLE, DOC_ALIAS);
        applyWhereWithOptionalCte(builder, criteria, authorizationSubjectIds, selectiveLegs);
        sortClause.applyOrdering(builder);
        if (limit != null) {
            builder.limit(Sql.bigint(limit));
        }
        if (offset > 0) {
            builder.offset(Sql.bigint(offset));
        }
        return Sql.render(builder.build());
    }

    /**
     * {@link #count(Criteria, java.util.List, long, long, SelectiveLegs)} with point legs only
     * ({@link SelectiveLegs#POINT}).
     *
     * @param criteria the query criteria.
     * @param authorizationSubjectIds the visibility subjects, or {@code null} for sudo.
     * @param skip the skip applied before counting (Mongo {@code CountOptions.skip}).
     * @param limit the cap on the count (Mongo {@code CountOptions.limit}; {@code <= 0} ⇒ uncapped).
     * @return the rendered, parameterized statement.
     */
    public static RenderedSql count(final Criteria criteria,
            @Nullable final java.util.List<String> authorizationSubjectIds, final long skip, final long limit) {
        return count(criteria, authorizationSubjectIds, skip, limit, SelectiveLegs.POINT);
    }

    /**
     * The {@code count}/{@code sudoCount} query — {@code count(*)} over the same {@code WHERE}. {@code skip}/{@code
     * limit} are mirrored from Mongo's {@code CountOptions} exactly: when either is set the counted rows are first
     * bounded by an {@code OFFSET}/{@code LIMIT} inside a {@code matched} CTE (no selective-leg CTE), else it is a plain
     * {@code count(*)} (which additionally gets the selective-leg CTE, like {@code findAll}).
     *
     * @param criteria the query criteria.
     * @param authorizationSubjectIds the visibility subjects, or {@code null} for sudo.
     * @param skip the skip applied before counting (Mongo {@code CountOptions.skip}).
     * @param limit the cap on the count (Mongo {@code CountOptions.limit}; {@code <= 0} ⇒ uncapped).
     * @param selectiveLegs which legs the selective-leg CTE may materialise.
     * @return the rendered, parameterized statement.
     */
    public static RenderedSql count(final Criteria criteria,
            @Nullable final java.util.List<String> authorizationSubjectIds, final long skip, final long limit,
            final SelectiveLegs selectiveLegs) {
        if (skip <= 0 && limit <= 0) {
            // Common count: plain count(*) + the selective-leg CTE (same as findAll).
            final Select.Builder builder = Sql.select().column(COUNT_STAR).from(DOC_TABLE, DOC_ALIAS);
            applyWhereWithOptionalCte(builder, criteria, authorizationSubjectIds, selectiveLegs);
            return Sql.render(builder.build());
        }
        final SqlExpression predicate = translate(criteria, authorizationSubjectIds);
        // skip/limit-bounded count: wrap the matching rows in a CTE bounded by OFFSET/LIMIT, then count them.
        final Select.Builder matched = Sql.select().column(ONE).from(DOC_TABLE, DOC_ALIAS).where(predicate);
        if (limit > 0) {
            matched.limit(Sql.bigint(limit));
        }
        if (skip > 0) {
            matched.offset(Sql.bigint(skip));
        }
        final Select outer = Sql.select().column(COUNT_STAR).from(COUNT_CTE)
                .with(COUNT_CTE, matched.build()).build();
        return Sql.render(outer);
    }

    /**
     * {@link #renderForDiagnostics(Criteria, java.util.List, PostgresSortClause, SelectiveLegs)} with point legs only
     * ({@link SelectiveLegs#POINT}).
     *
     * @param criteria the query criteria.
     * @param authorizationSubjectIds the visibility subjects, or {@code null} for sudo.
     * @param sortClause the D3 sort/paging translation.
     * @return the rendered {@code findAll} statement for a representative single page.
     */
    public static RenderedSql renderForDiagnostics(final Criteria criteria,
            @Nullable final java.util.List<String> authorizationSubjectIds, final PostgresSortClause sortClause) {
        return renderForDiagnostics(criteria, authorizationSubjectIds, sortClause, SelectiveLegs.POINT);
    }

    /**
     * @param criteria the query criteria.
     * @param authorizationSubjectIds the visibility subjects, or {@code null} for sudo.
     * @param sortClause the D3 sort/paging translation.
     * @param selectiveLegs which legs the selective-leg CTE may materialise.
     * @return the rendered {@code findAll} statement for a representative single page (limit 1, no skip) — the slow-query
     * diagnostics form ({@code $n} placeholders shown, sudo form when subjects are {@code null}).
     */
    public static RenderedSql renderForDiagnostics(final Criteria criteria,
            @Nullable final java.util.List<String> authorizationSubjectIds, final PostgresSortClause sortClause,
            final SelectiveLegs selectiveLegs) {
        return findAll(criteria, authorizationSubjectIds, sortClause, 1L, 0L, selectiveLegs);
    }

    private static void applyWhereWithOptionalCte(final Select.Builder builder, final Criteria criteria,
            @Nullable final java.util.List<String> authorizationSubjectIds, final SelectiveLegs selectiveLegs) {
        final SqlExpression predicate = translate(criteria, authorizationSubjectIds);
        final Optional<SelectiveLegCte.Prelude> prelude = SelectiveLegCte.extract(criteria, selectiveLegs);
        if (prelude.isPresent()) {
            prelude.get().applyTo(builder);
            builder.where(Sql.and(semiJoin(), predicate));
        } else {
            builder.where(predicate);
        }
    }

    private static SqlExpression semiJoin() {
        return Sql.exists(Sql.select().column(ONE).from(SelectiveLegCte.LEG_CTE, SEL_ALIAS)
                .where(Sql.eq(Sql.col(SEL_ALIAS, THING_ID), Sql.col(DOC_ALIAS, THING_ID)))
                .build());
    }

    private static SqlExpression translate(final Criteria criteria,
            @Nullable final java.util.List<String> authorizationSubjectIds) {
        return authorizationSubjectIds == null
                ? CreateSqlVisitor.sudoApply(criteria).predicate()
                : CreateSqlVisitor.apply(criteria, authorizationSubjectIds).predicate();
    }

    /** Non-parameterized fixed statements (no binds) used by the persistence directly. */
    public static final class Fixed {

        /** The namespace count report (plan §3.5): {@code SELECT namespace, count(*) … GROUP BY namespace}. */
        public static final String NAMESPACE_REPORT_SQL =
                "SELECT namespace, count(*) AS count FROM search_things GROUP BY namespace";

        private Fixed() {
            throw new AssertionError("no instances");
        }
    }
}
