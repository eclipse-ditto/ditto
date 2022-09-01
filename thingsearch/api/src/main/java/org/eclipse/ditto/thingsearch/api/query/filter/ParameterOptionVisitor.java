/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.api.query.filter;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.rql.query.QueryBuilder;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.rql.query.expression.FieldExpressionFactory;
import org.eclipse.ditto.rql.query.expression.SortFieldExpression;
import org.eclipse.ditto.thingsearch.model.CursorOption;
import org.eclipse.ditto.thingsearch.model.LimitOption;
import org.eclipse.ditto.thingsearch.model.Option;
import org.eclipse.ditto.thingsearch.model.OptionVisitor;
import org.eclipse.ditto.thingsearch.model.SizeOption;
import org.eclipse.ditto.thingsearch.model.SortOptionEntry;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * OptionVisitor for the parsed parameters. Goes through the list of option parameters and creates the persistence
 * specific output out of them.
 */
@AllValuesAreNonnullByDefault
public final class ParameterOptionVisitor implements OptionVisitor {

    private final QueryBuilder queryBuilder;
    private final FieldExpressionFactory fieldExpressionFactory;

    /**
     * Constructs a new {@code ParameterOptionVisitor} object.
     *
     * @param fieldExpressionFactory the FieldExpressionFactory.
     * @param queryBuilder the query builder to be used.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public ParameterOptionVisitor(final FieldExpressionFactory fieldExpressionFactory,
            final QueryBuilder queryBuilder) {
        this.fieldExpressionFactory = checkNotNull(fieldExpressionFactory, "field expression factory");
        this.queryBuilder = checkNotNull(queryBuilder, "query builder");
    }

    /**
     * Given a list of options, calls the visit method on every option with this visitor instance.
     *
     * @param options the list of options.
     * @return the visitor for chaining.
     */
    public ParameterOptionVisitor visitAll(final Iterable<Option> options) {
        options.forEach(o -> o.accept(this));
        return this;
    }

    @Override
    public void visit(final LimitOption limitOption) {
        checkNotNull(limitOption, "limit option");
        final long skip = limitOption.getOffset();
        final long limit = limitOption.getCount();
        queryBuilder.skip(skip).limit(limit);
    }

    @Override
    public void visit(final org.eclipse.ditto.thingsearch.model.SortOption sortOption) {
        checkNotNull(sortOption, "sort option");
        final List<SortOption> sortOptions = sortOption.getEntries()
                .stream()
                .map(this::mapSort)
                .toList();
        queryBuilder.sort(sortOptions);
    }

    @Override
    public void visit(final CursorOption cursorOption) {
        // do nothing; cursor is processed elsewhere
    }

    @Override
    public void visit(final SizeOption sizeOption) {
        queryBuilder.skip(0L).size(sizeOption.getSize());
    }

    private SortOption mapSort(final SortOptionEntry entry) {
        return new SortOption(determineSortField(entry.getPropertyPath()), determineSortDirection(entry.getOrder()));
    }

    private SortFieldExpression determineSortField(final JsonPointer key) {
        return fieldExpressionFactory.sortBy(key.toString());
    }

    private static SortDirection determineSortDirection(final SortOptionEntry.SortOrder order) {
        return order == SortOptionEntry.SortOrder.ASC ? SortDirection.ASC : SortDirection.DESC;
    }

}
