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
import org.eclipse.ditto.services.thingsearch.querymodel.query.AggregationBuilder;
import org.eclipse.ditto.services.thingsearch.querymodel.query.AggregationBuilderFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.query.PolicyRestrictedSearchAggregation;
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
 * responses with a corresponding {@link PolicyRestrictedSearchAggregation}.
 * <p>
 * This actor receives only messages which where emitted by API v. 2 requests.
 */
public final class AggregationQueryActor extends AbstractActor {

    /**
     * The name of this actor in the system.
     */
    public static final String ACTOR_NAME = "aggregationQueryActor";

    private final DiagnosticLoggingAdapter logger = LogUtil.obtain(this);

    private final ThingsFieldExpressionFactory fieldExpressionFactory;
    private final QueryFilterCriteriaFactory queryFilterCriteriaFactory;
    private final AggregationBuilderFactory aggregationBuilderFactory;
    private final RqlOptionParser rqlOptionParser;

    private AggregationQueryActor(final CriteriaFactory criteriaFactory,
            final ThingsFieldExpressionFactory fieldExpressionFactory,
            final AggregationBuilderFactory aggregationBuilderFactory) {
        this.fieldExpressionFactory = fieldExpressionFactory;
        this.aggregationBuilderFactory = aggregationBuilderFactory;
        this.queryFilterCriteriaFactory = new QueryFilterCriteriaFactory(criteriaFactory, fieldExpressionFactory);
        rqlOptionParser = new RqlOptionParser();
    }

    /**
     * Creates Akka configuration object Props for this AggregationQueryActor.
     *
     * @param criteriaFactory a factory to create criteria.
     * @param fieldExpressionFactory a factory to retrieve things field expressions.
     * @param aggregationBuilderFactory a factory to create a query builder.
     * @return the Akka configuration Props object.
     */
    public static Props props(final CriteriaFactory criteriaFactory,
            final ThingsFieldExpressionFactory fieldExpressionFactory,
            final AggregationBuilderFactory aggregationBuilderFactory) {
        return Props.create(AggregationQueryActor.class, new Creator<AggregationQueryActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public AggregationQueryActor create() {
                return new AggregationQueryActor(criteriaFactory, fieldExpressionFactory, aggregationBuilderFactory);
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
            logger.warning("Error when creating PolicyRestrictedSearchAggregation from Command: {}", e.getMessage());
            getSender().tell(e, getSelf());
        }
    }

    private void handleCountThings(final CountThings command) {
        final Criteria filterCriteria;
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Set<String> namespaces = command.getNamespaces().orElse(null);

        if (namespaces == null) {
            filterCriteria = queryFilterCriteriaFactory.filterCriteria(
                    command.getFilter().orElse(null), dittoHeaders);
        } else {
            filterCriteria = queryFilterCriteriaFactory.filterCriteriaRestrictedByNamespaces(
                    command.getFilter().orElse(null),
                    dittoHeaders, namespaces);
        }

        EnsureMonotonicityVisitor.apply(filterCriteria, dittoHeaders);

        final AggregationBuilder aggregationBuilder = aggregationBuilderFactory.newCountBuilder(filterCriteria)
                .authorizationSubjects(dittoHeaders.getAuthorizationContext().getAuthorizationSubjectIds());

        getSender().tell(aggregationBuilder.build(), getSelf());
    }

    private void handleQueryThings(final QueryThings command) {
        final Criteria filterCriteria;
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Set<String> namespaces = command.getNamespaces().orElse(null);

        if (namespaces == null) {
            filterCriteria = queryFilterCriteriaFactory.filterCriteria(
                    command.getFilter().orElse(null), dittoHeaders);
        } else {
            filterCriteria = queryFilterCriteriaFactory.filterCriteriaRestrictedByNamespaces(
                    command.getFilter().orElse(null),
                    dittoHeaders, namespaces);
        }

        EnsureMonotonicityVisitor.apply(filterCriteria, dittoHeaders);

        final AggregationBuilder aggregationBuilder = aggregationBuilderFactory.newBuilder(filterCriteria)
                .authorizationSubjects(dittoHeaders.getAuthorizationContext().getAuthorizationSubjectIds());

        command.getOptions()
                .map(optionStrings -> String.join(",", optionStrings))
                .ifPresent(options -> setOptions(options, aggregationBuilder, dittoHeaders));

        getSender().tell(aggregationBuilder.build(), getSelf());
    }

    private void handleSudoCountThings(final SudoCountThings command) {
        final Criteria criteria = queryFilterCriteriaFactory.filterCriteria(
                command.getFilter().orElse(null),
                command.getDittoHeaders());

        final AggregationBuilder aggregationBuilder = aggregationBuilderFactory.newCountBuilder(criteria);
        aggregationBuilder.sudo(true);

        getSender().tell(aggregationBuilder.build(), getSelf());
    }

    private void setOptions(final String options, final AggregationBuilder aggregationBuilder,
            final DittoHeaders dittoHeaders) {
        try {
            final AggregationParameterOptionVisitor visitor =
                    new AggregationParameterOptionVisitor(fieldExpressionFactory,
                            aggregationBuilder);
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
