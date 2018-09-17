/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.protocoladapter.TopicPath.Criterion.COMMANDS;
import static org.eclipse.ditto.protocoladapter.TopicPath.Criterion.EVENTS;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.FilteredTopic;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.model.query.things.ModelBasedThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.things.ThingPredicateVisitor;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithId;
import org.eclipse.ditto.signals.base.WithThingId;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingEventToThingConverter;

/**
 * Filters a set of targets by
 * <ul>
 * <li>removing those targets that do not want to receive a signal</li>
 * <li>removing those targets that are not allowed to read a signal</li>
 * </ul>
 */
class SignalFilter {

    private final Connection connection;
    private final QueryFilterCriteriaFactory queryFilterCriteriaFactory;

    /**
     * Constructs a new SignalFilter instance with the given {@code connection}.
     *
     * @param connection the connection to filter the signals on.
     */
    SignalFilter(final Connection connection) {
        this.connection = connection;
        final CriteriaFactory criteriaFactory = new CriteriaFactoryImpl();
        final ThingsFieldExpressionFactory fieldExpressionFactory =
                new ModelBasedThingsFieldExpressionFactory();
        queryFilterCriteriaFactory = new QueryFilterCriteriaFactory(criteriaFactory, fieldExpressionFactory);
    }

    /**
     * Filters the passed {@code signal} by extracting those {@link Target}s which should receive the signal.
     *
     * @param signal the signal to filter / determine the {@link Target}s for
     * @return the determined Targets for the passed in {@code signal}
     * @throws org.eclipse.ditto.model.rql.InvalidRqlExpressionException if the optional filter string of a Target
     * cannot be mapped to a valid criterion
     */
    Set<Target> filter(final Signal<?> signal) {
        return connection.getTargets().stream()
                .filter(t -> isTargetAuthorized(t, signal)) // this is cheaper, so check this first
                .filter(t -> isTargetSubscribedForTopic(t, signal))
                .collect(Collectors.toSet());
    }

    private static boolean isTargetAuthorized(final Target target, final Signal<?> signal) {
        final Set<String> authorizedReadSubjects = signal.getDittoHeaders().getReadSubjects();
        final AuthorizationContext authorizationContext = target.getAuthorizationContext();
        final List<String> connectionSubjects = authorizationContext.getAuthorizationSubjectIds();
        return !Collections.disjoint(authorizedReadSubjects, connectionSubjects);
    }

    private boolean isTargetSubscribedForTopic(final Target target, final Signal<?> signal) {
        return target.getTopics().stream()
                .filter(applyTopicFilter(signal))
                .filter(applyRqlFilter(signal))
                .anyMatch(applyNamespaceFilter(signal));
    }

    private static Predicate<FilteredTopic> applyTopicFilter(final Signal<?> signal) {
        return t -> t.getTopic().equals(topicFromSignal(signal).orElse(null));
    }

    private Predicate<FilteredTopic> applyRqlFilter(final Signal<?> signal) {
        return t -> !t.hasFilter() || t.getFilter().filter(f -> matchesFilter(f, signal)).isPresent();
    }

    private static Predicate<FilteredTopic> applyNamespaceFilter(final WithId signal) {
        return t -> t.getNamespaces().isEmpty() || t.getNamespaces().contains(namespaceFromId(signal));
    }

    private static String namespaceFromId(final WithId withId) {
        return withId.getId().split(":", 2)[0];
    }

    private boolean matchesFilter(final String filter, final Signal<?> signal) {

        if (signal instanceof ThingEvent) {

            // currently only ThingEvents may be filtered
            return ThingEventToThingConverter.thingEventToThing((ThingEvent) signal)
                    .filter(thing -> ThingPredicateVisitor.apply(parseCriteria(filter, signal.getDittoHeaders()))
                            .test(thing)
                    )
                    .isPresent();
        } else {
            return true;
        }
    }

    /**
     * @throws org.eclipse.ditto.model.rql.InvalidRqlExpressionException if the filter string cannot be mapped to a
     * valid criterion
     */
    private Criteria parseCriteria(final String filter, final DittoHeaders dittoHeaders) {
        return queryFilterCriteriaFactory.filterCriteria(filter, dittoHeaders);
    }

    private static Optional<Topic> topicFromSignal(final Signal<?> signal) {
        // only things as group supported
        final TopicPath.Group group = signal instanceof WithThingId ? TopicPath.Group.THINGS : null;
        final TopicPath.Channel channel = signal.getDittoHeaders()
                .getChannel()
                .flatMap(TopicPath.Channel::forName)
                .orElse(TopicPath.Channel.TWIN);
        final TopicPath.Criterion criterion = getCriterionOfSignal(signal);

        if (TopicPath.Group.THINGS.equals(group)) {
            if (TopicPath.Channel.TWIN.equals(channel)) {
                if (EVENTS.equals(criterion)) {
                    return Optional.of(Topic.TWIN_EVENTS);
                }
            } else if (TopicPath.Channel.LIVE.equals(channel) && criterion != null) {
                switch (criterion) {
                    case COMMANDS:
                        return Optional.of(Topic.LIVE_COMMANDS);
                    case EVENTS:
                        return Optional.of(Topic.LIVE_EVENTS);
                    case MESSAGES:
                        return Optional.of(Topic.LIVE_MESSAGES);
                    default:
                        return Optional.empty();
                }
            }
        }

        return Optional.empty();
    }

    @Nullable
    private static TopicPath.Criterion getCriterionOfSignal(final Signal<?> signal) {
        final TopicPath.Criterion criterion;
        if (signal instanceof MessageCommand || signal instanceof MessageCommandResponse) {
            criterion = TopicPath.Criterion.MESSAGES;
        } else if (signal instanceof Command || signal instanceof CommandResponse) {
            criterion = COMMANDS;
        } else if (signal instanceof Event) {
            criterion = EVENTS;
        } else {
            criterion = null;
        }
        return criterion;
    }

}
