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
package org.eclipse.ditto.services.models.thingsearch.query.filter;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.query.QueryBuilder;
import org.eclipse.ditto.model.query.SortDirection;
import org.eclipse.ditto.model.query.SortOption;
import org.eclipse.ditto.model.query.expression.FieldExpressionFactory;
import org.eclipse.ditto.model.query.expression.SortFieldExpression;
import org.eclipse.ditto.model.thingsearch.LimitOption;
import org.eclipse.ditto.model.thingsearch.Option;
import org.eclipse.ditto.model.thingsearch.OptionVisitor;
import org.eclipse.ditto.model.thingsearch.SortOptionEntry;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * OptionVisitor for the parsed parameters. Goes through the list of option parameters and creates the persistence
 * specific output out of them.
 */
// TODO: replace this.
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
    public ParameterOptionVisitor(final FieldExpressionFactory fieldExpressionFactory, final QueryBuilder queryBuilder) {
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
        // TODO: not required; remove.
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
