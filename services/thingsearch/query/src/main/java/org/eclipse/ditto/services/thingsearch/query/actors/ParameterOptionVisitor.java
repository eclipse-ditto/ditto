/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.query.actors;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.thingsearch.LimitOption;
import org.eclipse.ditto.model.thingsearch.Option;
import org.eclipse.ditto.model.thingsearch.OptionVisitor;
import org.eclipse.ditto.model.thingsearch.SortOptionEntry;

import org.eclipse.ditto.services.thingsearch.querymodel.expression.FieldExpressionFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.SortFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.query.QueryBuilder;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortDirection;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortOption;

/**
 * OptionVisitor for the parsed parameters. Goes through the list of option parameters and creates the persistence
 * specific output out of them.
 */
final class ParameterOptionVisitor implements OptionVisitor {

    private final QueryBuilder queryBuilder;
    private final FieldExpressionFactory fieldExpressionFactory;

    /**
     * Constructs a new {@code ParameterOptionVisitor} object.
     * 
     * @param fieldExpressionFactory the FieldExpressionFactory.
     * @param queryBuilder the query builder to be used.
     * @throws NullPointerException if any argument is {@code null}.
     */
    ParameterOptionVisitor(final FieldExpressionFactory fieldExpressionFactory, final QueryBuilder queryBuilder) {
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
    public void visit(final org.eclipse.ditto.model.thingsearch.SortOption sortOption) {
        checkNotNull(sortOption, "sort option");
        final List<SortOption> sortOptions = sortOption.getEntries()
                .stream()
                .map(this::mapSort)
                .collect(Collectors.toList());
        queryBuilder.sort(sortOptions);
    }

    @Override
    public void visit(final Option option) {
        // not required yet
    }

    private SortOption mapSort(final SortOptionEntry entry) {
        return new SortOption(determineSortField(entry.getPropertyPath()), determineSortDirection(entry.getOrder()));
    }

    private SortFieldExpression determineSortField(final JsonPointer key) {
        final String name = key.toString().replaceFirst("/", "");
        return fieldExpressionFactory.sortBy(name);
    }

    private static SortDirection determineSortDirection(final SortOptionEntry.SortOrder order) {
        return order == SortOptionEntry.SortOrder.ASC ? SortDirection.ASC : SortDirection.DESC;
    }

}
