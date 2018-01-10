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

import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.thingsearchparser.ParserException;
import org.eclipse.ditto.model.thingsearchparser.options.rql.RqlOptionParser;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SudoCountThings;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.query.Query;
import org.eclipse.ditto.services.thingsearch.querymodel.query.QueryBuilder;
import org.eclipse.ditto.services.thingsearch.querymodel.query.QueryBuilderFactory;
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

    private final QueryFilterCriteriaFactory queryFilterCriteriaFactory;
    private final ThingsFieldExpressionFactory fieldExpressionFactory;
    private final QueryBuilderFactory queryBuilderFactory;
    private final RqlOptionParser rqlOptionParser;

    private QueryActor(final CriteriaFactory criteriaFactory, final ThingsFieldExpressionFactory fieldExpressionFactory,
            final QueryBuilderFactory queryBuilderFactory) {

        this.queryFilterCriteriaFactory = new QueryFilterCriteriaFactory(criteriaFactory, fieldExpressionFactory);
        this.fieldExpressionFactory = fieldExpressionFactory;
        this.queryBuilderFactory = queryBuilderFactory;
        rqlOptionParser = new RqlOptionParser();
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
            public QueryActor create() {
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
        final Criteria filterCriteria;
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Set<String> namespaces = command.getNamespaces().orElse(null);

        if (namespaces == null) {
            filterCriteria = queryFilterCriteriaFactory.filterCriteriaRestrictedByAcl(
                    command.getFilter().orElse(null), dittoHeaders,
                    dittoHeaders.getAuthorizationContext().getAuthorizationSubjectIds());
        } else {
            filterCriteria = queryFilterCriteriaFactory.filterCriteriaRestrictedByAclAndNamespaces(
                    command.getFilter().orElse(null), dittoHeaders,
                    dittoHeaders.getAuthorizationContext().getAuthorizationSubjectIds(),
                    namespaces);
        }

        final QueryBuilder queryBuilder = queryBuilderFactory.newUnlimitedBuilder(filterCriteria);

        getSender().tell(queryBuilder.build(), getSelf());
    }

    private void handleQueryThings(final QueryThings command) {
        final Criteria filterCriteria;
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Set<String> namespaces = command.getNamespaces().orElse(null);

        if (namespaces == null) {
            filterCriteria = queryFilterCriteriaFactory.filterCriteriaRestrictedByAcl(
                    command.getFilter().orElse(null), dittoHeaders,
                    dittoHeaders.getAuthorizationContext().getAuthorizationSubjectIds());
        } else {
            filterCriteria = queryFilterCriteriaFactory.filterCriteriaRestrictedByAclAndNamespaces(
                    command.getFilter().orElse(null), dittoHeaders,
                    dittoHeaders.getAuthorizationContext().getAuthorizationSubjectIds(),
                    namespaces);
        }

        final QueryBuilder queryBuilder = queryBuilderFactory.newBuilder(filterCriteria);

        command.getOptions()
                .map(optionStrings -> String.join(",", optionStrings))
                .ifPresent(options -> setOptions(options, queryBuilder, dittoHeaders));

        getSender().tell(queryBuilder.build(), getSelf());
    }

    private void handleSudoCountThings(final SudoCountThings command) {
        final Criteria filterCriteria = queryFilterCriteriaFactory.filterCriteria(
                command.getFilter().orElse(null), command.getDittoHeaders());

        final QueryBuilder queryBuilder = queryBuilderFactory.newUnlimitedBuilder(filterCriteria);

        getSender().tell(queryBuilder.build(), getSelf());
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
