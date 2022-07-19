/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.query;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.rql.model.ParserException;
import org.eclipse.ditto.rql.model.predicates.PredicateParser;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.parser.thingsearch.RqlOptionParser;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.QueryBuilder;
import org.eclipse.ditto.rql.query.QueryBuilderFactory;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.thingsearch.api.commands.sudo.StreamThings;
import org.eclipse.ditto.thingsearch.api.commands.sudo.SudoCountThings;
import org.eclipse.ditto.thingsearch.api.query.filter.ParameterOptionVisitor;
import org.eclipse.ditto.thingsearch.model.signals.commands.exceptions.InvalidOptionException;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.ThingSearchQueryCommand;
import org.eclipse.ditto.thingsearch.service.persistence.query.validation.QueryCriteriaValidator;

/**
 * Create Query objects from search commands.
 */
public final class QueryParser {

    private final QueryFilterCriteriaFactory queryFilterCriteriaFactory;
    private final ThingsFieldExpressionFactory fieldExpressionFactory;
    private final QueryBuilderFactory queryBuilderFactory;
    private final RqlOptionParser rqlOptionParser;
    private final QueryCriteriaValidator queryCriteriaValidator;

    private QueryParser(final ThingsFieldExpressionFactory fieldExpressionFactory,
            final PredicateParser predicateParser,
            final QueryBuilderFactory queryBuilderFactory,
            final QueryCriteriaValidator queryCriteriaValidator) {

        this.queryFilterCriteriaFactory = QueryFilterCriteriaFactory.of(fieldExpressionFactory, predicateParser);
        this.fieldExpressionFactory = fieldExpressionFactory;
        this.queryBuilderFactory = queryBuilderFactory;
        this.queryCriteriaValidator = queryCriteriaValidator;
        rqlOptionParser = new RqlOptionParser();
    }

    /**
     * Create a QueryFactory.
     *
     * @param fieldExpressionFactory a factory to retrieve things field expressions.
     * @param queryBuilderFactory a factory to create a query builder.
     * @param queryCriteriaValidator a validator for queries.
     * @return the query factory.
     */
    public static QueryParser of(final ThingsFieldExpressionFactory fieldExpressionFactory,
            final QueryBuilderFactory queryBuilderFactory,
            final QueryCriteriaValidator queryCriteriaValidator) {

        return new QueryParser(fieldExpressionFactory, RqlPredicateParser.getInstance(), queryBuilderFactory,
                queryCriteriaValidator);
    }

    /**
     * Parses a search command into a query.
     *
     * @param command the search command.
     * @return the query.
     */
    public CompletionStage<Query> parse(final ThingSearchQueryCommand<?> command) {
        final Criteria criteria = parseCriteria(command);
        final Query query;
        if (command instanceof final QueryThings queryThings) {
            final QueryBuilder queryBuilder = queryBuilderFactory.newBuilder(criteria);
            queryThings.getOptions()
                    .map(optionStrings -> String.join(",", optionStrings))
                    .ifPresent(options -> setOptions(options, queryBuilder, command.getDittoHeaders()));
            query = queryBuilder.build();
        } else if (command instanceof final StreamThings streamThings) {
            final QueryBuilder queryBuilder = queryBuilderFactory.newUnlimitedBuilder(criteria);
            streamThings.getSort().ifPresent(sort -> setOptions(sort, queryBuilder, command.getDittoHeaders()));
            query = queryBuilder.build();
        } else {
            query = queryBuilderFactory.newUnlimitedBuilder(criteria).build();
        }
        return queryCriteriaValidator.validateQuery(command, query);
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

    /**
     * Parses a SudoCountThings command into a query.
     *
     * @param sudoCountThings the command.
     * @return the query.
     */
    public CompletionStage<Query> parseSudoCountThings(final SudoCountThings sudoCountThings) {
        final DittoHeaders headers = sudoCountThings.getDittoHeaders();
        final String filters = sudoCountThings.getFilter().orElse(null);
        final Criteria criteria = queryFilterCriteriaFactory.filterCriteria(filters, headers);
        return CompletableFuture.completedStage(queryBuilderFactory.newUnlimitedBuilder(criteria).build());
    }

    /**
     * @return the criteria factory.
     */
    public CriteriaFactory getCriteriaFactory() {
        return queryFilterCriteriaFactory.toCriteriaFactory();
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
