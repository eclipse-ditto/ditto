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

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.thingsearchparser.ParserException;
import org.eclipse.ditto.model.thingsearchparser.options.rql.RqlOptionParser;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.RootNode;
import org.eclipse.ditto.model.thingsearchparser.predicates.rql.RqlPredicateParser;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SudoCountThings;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.InvalidFilterException;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.InvalidOptionException;
import org.eclipse.ditto.signals.commands.thingsearch.query.CountThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Predicate;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FilterFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.query.Query;
import org.eclipse.ditto.services.thingsearch.querymodel.query.QueryBuilder;
import org.eclipse.ditto.services.thingsearch.querymodel.query.QueryBuilderFactory;

/**
 * Actor handling the parsing of search queries. It accepts {@link CountThings} and {@link QueryThings} commands and
 * responses with a corresponding {@link Query}.
 * <p>
 * This actor receives only messages which where emitted by API v. 1 requests.
 */
public final class QueryActor extends AbstractActor {

    /**
     * The name of this actor in the system.
     */
    public static final String ACTOR_NAME = "queryActor";

    private final DiagnosticLoggingAdapter logger = LogUtil.obtain(this);

    private final CriteriaFactory criteriaFactory;
    private final ThingsFieldExpressionFactory fieldExpressionFactory;
    private final QueryBuilderFactory queryBuilderFactory;
    private final RqlOptionParser rqlOptionParser;
    private final RqlPredicateParser rqlPredicateParser;

    private QueryActor(final CriteriaFactory criteriaFactory, final ThingsFieldExpressionFactory fieldExpressionFactory,
            final QueryBuilderFactory queryBuilderFactory) {

        this.criteriaFactory = criteriaFactory;
        this.fieldExpressionFactory = fieldExpressionFactory;
        this.queryBuilderFactory = queryBuilderFactory;
        rqlOptionParser = new RqlOptionParser();
        rqlPredicateParser = new RqlPredicateParser();
    }

    /**
     * Creates Akka configuration object Props for this QueryActor.
     *
     * @param criteriaFactory a factory to create criteria.
     * @param fieldExpressionFactory a factory to retrieve things field expressions.
     * @param queryBuilderFactory a factory to create a query builder.
     * @return the Akka configuration Props object.
     */
    public static Props props(final CriteriaFactory criteriaFactory,
            final ThingsFieldExpressionFactory fieldExpressionFactory, final QueryBuilderFactory queryBuilderFactory) {

        return Props.create(QueryActor.class, new Creator<QueryActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public QueryActor create() throws Exception {
                return new QueryActor(criteriaFactory, fieldExpressionFactory, queryBuilderFactory);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(CountThings.class, cmd -> catchDittoRuntimeException(this::handleCountThings, cmd))
                .match(QueryThings.class, cmd -> catchDittoRuntimeException(this::handleQueryThings, cmd))
                .match(SudoCountThings.class, cmd -> catchDittoRuntimeException(this::handleSudoCountThings, cmd))
                .matchAny(any -> {
                    logger.warning("Got unknown message '{}'", any);
                    getContext().stop(getSelf());
                }).build();
    }

    private <T extends Command> void catchDittoRuntimeException(final Consumer<T> consumer, final T command) {
        try {
            consumer.accept(command);
        } catch (final InvalidFilterException | InvalidOptionException e) {
            LogUtil.enhanceLogWithCorrelationId(logger, command);
            logger.info("Error when creating Query from Command: {}", e.getMessage());
            getSender().tell(e, getSelf());
        }
    }

    private void handleCountThings(final CountThings command) {
        final Criteria criteria = command.getFilter()
                .map(filterString -> mapCriteria(filterString, command.getDittoHeaders()))
                .orElse(criteriaFactory.any());
        final Criteria filteredCriteria =
                addAclFilter(criteria, command.getDittoHeaders().getAuthorizationContext());
        final QueryBuilder queryBuilder = queryBuilderFactory.newUnlimitedBuilder(filteredCriteria);

        getSender().tell(queryBuilder.build(), getSelf());
    }

    private void handleQueryThings(final QueryThings command) {
        final Criteria criteria = command.getFilter()
                .map(filterString -> mapCriteria(filterString, command.getDittoHeaders()))
                .orElse(criteriaFactory.any());
        final Criteria filteredCriteria = addAclFilter(criteria, command.getDittoHeaders().getAuthorizationContext());
        final QueryBuilder queryBuilder = queryBuilderFactory.newBuilder(filteredCriteria);

        command.getOptions()
                .map(optionStrings -> String.join(",", optionStrings))
                .ifPresent(options -> setOptions(options, queryBuilder, command.getDittoHeaders()));

        getSender().tell(queryBuilder.build(), getSelf());
    }

    private void handleSudoCountThings(final SudoCountThings command) {
        final Criteria criteria = command.getFilter()
                .map(filterString -> mapCriteria(filterString, command.getDittoHeaders()))
                .orElse(criteriaFactory.any());
        final QueryBuilder queryBuilder = queryBuilderFactory.newUnlimitedBuilder(criteria);

        getSender().tell(queryBuilder.build(), getSelf());
    }

    private Criteria addAclFilter(final Criteria criteria, final AuthorizationContext authorizationContext) {
        final List<Object> sids = authorizationContext.getAuthorizationSubjects().stream()
                .map(AuthorizationSubject::getId)
                .collect(Collectors.toList());
        final FilterFieldExpression aclFieldExpression = fieldExpressionFactory.filterByAcl();
        final Predicate sidPredicate = criteriaFactory.in(sids);
        final Criteria aclFieldCriteria = criteriaFactory.fieldCriteria(aclFieldExpression, sidPredicate);

        return criteriaFactory.and(Arrays.asList(criteria, aclFieldCriteria));
    }

    private Criteria mapCriteria(final String filter, final DittoHeaders dittoHeaders) {
        try {
            final ParameterPredicateVisitor visitor =
                    new ParameterPredicateVisitor(criteriaFactory, fieldExpressionFactory);

            final RootNode rootNode = rqlPredicateParser.parse(filter);
            visitor.visit(rootNode);

            final Criteria criteria;
            if (visitor.getCriteria().size() > 1) {
                criteria = criteriaFactory.and(visitor.getCriteria());
            } else if (visitor.getCriteria().size() == 1) {
                criteria = visitor.getCriteria().get(0);
            } else {
                criteria = criteriaFactory.any();
            }
            return criteria;
        } catch (final ParserException | IllegalArgumentException e) {
            throw InvalidFilterException.newBuilder()
                    .message(e.getMessage())
                    .cause(e)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    private void setOptions(final String options, final QueryBuilder queryBuilder,
            final DittoHeaders dittoHeaders) {
        try {
            final ParameterOptionVisitor visitor = new ParameterOptionVisitor(fieldExpressionFactory, queryBuilder);
            visitor.visitAll(rqlOptionParser.parse(options));
        } catch (final ParserException | IllegalArgumentException e) {
            throw InvalidOptionException.newBuilder()
                    .message(e.getMessage())
                    .cause(e)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

}
