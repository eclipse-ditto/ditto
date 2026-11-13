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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * A {@code SELECT} statement (top-level query, {@code EXISTS} subquery, CTE body or lateral block), assembled via
 * {@link #builder()}. Renders as:
 * <pre>{@code [WITH [MATERIALIZED] cte, …] SELECT col[ AS a], … FROM table[ alias][ LEFT JOIN LATERAL …]
 *   [ WHERE …][ GROUP BY …][ UNION ALL <select> …][ ORDER BY key[ DESC], …][ LIMIT …][ OFFSET …]}</pre>
 * All value/path/pattern operands within it are {@code $n} binds; only the fixed grammar and validated identifiers are
 * rendered as text. The builder takes only public types + validated identifier strings, so the structural sub-nodes
 * ({@code SelectColumn}, {@code TableRef}, {@code Cte}, {@code LateralJoin}) stay package-internal.
 */
public final class Select implements SqlNode {

    private final List<Cte> ctes;
    private final List<SelectColumn> columns;
    private final SqlNode from;
    private final List<LateralJoin> joins;
    @Nullable
    private final SqlExpression where;
    private final List<SqlExpression> groupBy;
    private final List<Select> unionAll;
    private final List<OrderByItem> orderBy;
    @Nullable
    private final SqlExpression limit;
    @Nullable
    private final SqlExpression offset;

    private Select(final Builder builder) {
        if (builder.columns.isEmpty()) {
            throw new IllegalArgumentException("SELECT requires at least one column");
        }
        if (builder.from == null) {
            throw new IllegalArgumentException("SELECT requires a FROM source");
        }
        this.ctes = List.copyOf(builder.ctes);
        this.columns = List.copyOf(builder.columns);
        this.from = builder.from;
        this.joins = List.copyOf(builder.joins);
        this.where = builder.where;
        this.groupBy = List.copyOf(builder.groupBy);
        this.unionAll = List.copyOf(builder.unionAll);
        this.orderBy = List.copyOf(builder.orderBy);
        this.limit = builder.limit;
        this.offset = builder.offset;
    }

    /**
     * @return a new, empty {@code SELECT} builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        if (!ctes.isEmpty()) {
            ctx.sql("WITH ").renderSeparated(ctes, ", ").sql(" ");
        }
        ctx.sql("SELECT ").renderSeparated(columns, ", ");
        ctx.sql(" FROM ").render(from);
        for (final LateralJoin join : joins) {
            ctx.sql(" ").render(join);
        }
        if (where != null) {
            ctx.sql(" WHERE ").render(where);
        }
        if (!groupBy.isEmpty()) {
            ctx.sql(" GROUP BY ").renderSeparated(groupBy, ", ");
        }
        for (final Select branch : unionAll) {
            ctx.sql(" UNION ALL ").render(branch);
        }
        if (!orderBy.isEmpty()) {
            ctx.sql(" ORDER BY ").renderSeparated(orderBy, ", ");
        }
        if (limit != null) {
            ctx.sql(" LIMIT ").render(limit);
        }
        if (offset != null) {
            ctx.sql(" OFFSET ").render(offset);
        }
    }

    /**
     * A fluent builder for {@link Select} taking only public expression types and validated identifier strings.
     */
    public static final class Builder {

        private final List<Cte> ctes = new ArrayList<>();
        private final List<SelectColumn> columns = new ArrayList<>();
        @Nullable
        private SqlNode from;
        private final List<LateralJoin> joins = new ArrayList<>();
        @Nullable
        private SqlExpression where;
        private final List<SqlExpression> groupBy = new ArrayList<>();
        private final List<Select> unionAll = new ArrayList<>();
        private final List<OrderByItem> orderBy = new ArrayList<>();
        @Nullable
        private SqlExpression limit;
        @Nullable
        private SqlExpression offset;

        private Builder() {}

        /**
         * Adds a projected column with no alias.
         *
         * @param expression the column expression.
         * @return this builder.
         */
        public Builder column(final SqlExpression expression) {
            columns.add(new SelectColumn(expression, null));
            return this;
        }

        /**
         * Adds a projected column with an {@code AS <alias>} (alias validated as a bare identifier).
         *
         * @param expression the column expression.
         * @param alias the column alias.
         * @return this builder.
         */
        public Builder column(final SqlExpression expression, final String alias) {
            columns.add(new SelectColumn(expression, alias));
            return this;
        }

        /**
         * Sets the {@code FROM} table/CTE (no alias).
         *
         * @param table the table or CTE name (validated identifier).
         * @return this builder.
         */
        public Builder from(final String table) {
            this.from = new TableRef(table, null);
            return this;
        }

        /**
         * Sets the {@code FROM} table/CTE with an alias.
         *
         * @param table the table or CTE name (validated identifier).
         * @param alias the table alias (validated identifier).
         * @return this builder.
         */
        public Builder from(final String table, final String alias) {
            this.from = new TableRef(table, alias);
            return this;
        }

        /**
         * Sets a subquery as the {@code FROM} source: {@code FROM (<subquery>) <alias>}.
         *
         * @param subquery the subquery.
         * @param alias its (validated) alias.
         * @return this builder.
         */
        public Builder from(final Select subquery, final String alias) {
            this.from = new DerivedTable(subquery, alias);
            return this;
        }

        /**
         * Adds a {@code LEFT JOIN LATERAL (subquery) alias ON true} block.
         *
         * @param subquery the lateral subquery.
         * @param alias the lateral alias (validated identifier).
         * @return this builder.
         */
        public Builder leftJoinLateral(final Select subquery, final String alias) {
            joins.add(new LateralJoin(subquery, alias));
            return this;
        }

        /**
         * Sets the {@code WHERE} predicate.
         *
         * @param whereExpression the predicate.
         * @return this builder.
         */
        public Builder where(final SqlExpression whereExpression) {
            this.where = whereExpression;
            return this;
        }

        /**
         * Adds a {@code GROUP BY} key (rendered after {@code WHERE}, before {@code ORDER BY}). Typically an output-column
         * ordinal ({@code Sql.raw("1")}) or a bare grouping expression.
         *
         * @param expression the grouping key.
         * @return this builder.
         */
        public Builder groupBy(final SqlExpression expression) {
            groupBy.add(expression);
            return this;
        }

        /**
         * Appends {@code UNION ALL <branch>}. This builder's {@code ORDER BY}/{@code LIMIT}/{@code OFFSET} then apply to
         * the whole union (PostgreSQL semantics), so a branch must be a plain {@code SELECT}: no {@code WITH},
         * {@code ORDER BY}, {@code LIMIT}, {@code OFFSET} or union of its own.
         *
         * @param branch the branch.
         * @return this builder.
         * @throws IllegalArgumentException if the branch is not a plain {@code SELECT}.
         */
        public Builder unionAll(final Select branch) {
            if (!branch.ctes.isEmpty() || !branch.orderBy.isEmpty() || branch.limit != null || branch.offset != null
                    || !branch.unionAll.isEmpty()) {
                throw new IllegalArgumentException("A UNION ALL branch must be a plain SELECT (no WITH, ORDER BY, "
                        + "LIMIT, OFFSET or UNION of its own)");
            }
            unionAll.add(branch);
            return this;
        }

        /**
         * Adds an ascending {@code ORDER BY} key.
         *
         * @param expression the sort key.
         * @return this builder.
         */
        public Builder orderByAsc(final SqlExpression expression) {
            orderBy.add(new OrderByItem(expression, false));
            return this;
        }

        /**
         * Adds a descending {@code ORDER BY} key.
         *
         * @param expression the sort key.
         * @return this builder.
         */
        public Builder orderByDesc(final SqlExpression expression) {
            orderBy.add(new OrderByItem(expression, true));
            return this;
        }

        /**
         * Sets the {@code LIMIT}.
         *
         * @param limitExpression the limit expression (bind or fixed).
         * @return this builder.
         */
        public Builder limit(final SqlExpression limitExpression) {
            this.limit = limitExpression;
            return this;
        }

        /**
         * Sets the {@code OFFSET}.
         *
         * @param offsetExpression the offset expression (bind or fixed).
         * @return this builder.
         */
        public Builder offset(final SqlExpression offsetExpression) {
            this.offset = offsetExpression;
            return this;
        }

        /**
         * Appends a non-materialized common-table-expression to the {@code WITH} clause (CTEs render in call order; a
         * CTE that reads another must be added after it).
         *
         * @param name the CTE name (validated identifier).
         * @param subquery the CTE body.
         * @return this builder.
         */
        public Builder with(final String name, final Select subquery) {
            ctes.add(new Cte(name, false, subquery));
            return this;
        }

        /**
         * Appends a {@code MATERIALIZED} common-table-expression to the {@code WITH} clause (§3.5 rescue form; CTEs
         * render in call order; a CTE that reads another must be added after it).
         *
         * @param name the CTE name (validated identifier).
         * @param subquery the CTE body.
         * @return this builder.
         */
        public Builder withMaterialized(final String name, final Select subquery) {
            ctes.add(new Cte(name, true, subquery));
            return this;
        }

        /**
         * @return the immutable {@link Select}.
         */
        public Select build() {
            return new Select(this);
        }

    }

}
