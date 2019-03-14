/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.persistence.query;

import java.util.Set;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.query.Query;
import org.eclipse.ditto.model.query.QueryBuilder;
import org.eclipse.ditto.model.query.QueryBuilderFactory;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.model.rql.ParserException;
import org.eclipse.ditto.model.thingsearchparser.RqlOptionParser;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SudoCountThings;
import org.eclipse.ditto.services.models.thingsearch.query.filter.ParameterOptionVisitor;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.InvalidOptionException;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.ThingSearchQueryCommand;

/**
 * Create Query objects from search commands.
 */
public final class QueryParser {

    private final QueryFilterCriteriaFactory queryFilterCriteriaFactory;
    private final ThingsFieldExpressionFactory fieldExpressionFactory;
    private final QueryBuilderFactory queryBuilderFactory;
    private final RqlOptionParser rqlOptionParser;

    private QueryParser(final CriteriaFactory criteriaFactory,
            final ThingsFieldExpressionFactory fieldExpressionFactory,
            final QueryBuilderFactory queryBuilderFactory) {

        this.queryFilterCriteriaFactory = new QueryFilterCriteriaFactory(criteriaFactory, fieldExpressionFactory);
        this.fieldExpressionFactory = fieldExpressionFactory;
        this.queryBuilderFactory = queryBuilderFactory;
        rqlOptionParser = new RqlOptionParser();
    }

    /**
     * Create a QueryFactory.
     *
     * @param criteriaFactory a factory to create criteria.
     * @param fieldExpressionFactory a factory to retrieve things field expressions.
     * @param queryBuilderFactory a factory to create a query builder.
     * @return the query factory.
     */
    public static QueryParser of(final CriteriaFactory criteriaFactory,
            final ThingsFieldExpressionFactory fieldExpressionFactory,
            final QueryBuilderFactory queryBuilderFactory) {

        return new QueryParser(criteriaFactory, fieldExpressionFactory, queryBuilderFactory);
    }

    /**
     * Parses a search command into a query.
     *
     * @param command the search command.
     * @return the query.
     */
    public Query parse(final ThingSearchQueryCommand<?> command) {
        final Criteria criteria = parseCriteria(command);
        if (command instanceof QueryThings) {
            final QueryThings queryThings = (QueryThings) command;
            final QueryBuilder queryBuilder = queryBuilderFactory.newBuilder(criteria);
            queryThings.getOptions()
                    .map(optionStrings -> String.join(",", optionStrings))
                    .ifPresent(options -> setOptions(options, queryBuilder, command.getDittoHeaders()));
            return queryBuilder.build();
        } else {
            return queryBuilderFactory.newUnlimitedBuilder(criteria).build();
        }
    }

    /**
     * Parses a SudoCountThings command into a query.
     *
     * @param sudoCountThings the command.
     * @return the query.
     */
    public Query parseSudoCountThings(final SudoCountThings sudoCountThings) {
        final DittoHeaders headers = sudoCountThings.getDittoHeaders();
        final String filters = sudoCountThings.getFilter().orElse(null);
        final Criteria criteria = queryFilterCriteriaFactory.filterCriteria(filters, headers);
        return queryBuilderFactory.newUnlimitedBuilder(criteria).build();
    }

    private Criteria parseCriteria(final ThingSearchQueryCommand<?> command) {
        final DittoHeaders headers = command.getDittoHeaders();
        final Set<String> namespaces = command.getNamespaces().orElse(null);
        final String filter = command.getFilter().orElse(null);
        if (namespaces == null) {
            return queryFilterCriteriaFactory.filterCriteria(filter, command.getDittoHeaders());
        } else {
            return queryFilterCriteriaFactory.filterCriteriaRestrictedByNamespaces(filter, headers, namespaces);
        }
    }

    private void setOptions(final String options, final QueryBuilder queryBuilder, final DittoHeaders headers) {
        try {
            final ParameterOptionVisitor visitor = new ParameterOptionVisitor(fieldExpressionFactory, queryBuilder);
            visitor.visitAll(rqlOptionParser.parse(options));
        } catch (final ParserException | IllegalArgumentException e) {
            throw InvalidOptionException.newBuilder()
                    .message(e.getMessage())
                    .cause(e)
                    .dittoHeaders(headers)
                    .build();
        }
    }
}
