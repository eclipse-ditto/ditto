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
import org.eclipse.ditto.model.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.model.query.model.criteria.Criteria;
import org.eclipse.ditto.model.query.model.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.model.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.model.expression.ThingsFieldExpressionFactory;
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
public class SignalFilter {


    public static Set<Target> filter(final Connection connection, final Signal<?> signal) {
        return connection.getTargets().stream()
                .filter(t -> isTargetSubscribedForTopic(t, signal))
                .filter(t -> isTargetAuthorized(t, signal))
                .collect(Collectors.toSet());
    }

    private static boolean isTargetSubscribedForTopic(final Target t, final Signal<?> signal) {
        return t.getTopics().stream()
                .filter(applyTopicFilter(signal))
                .filter(applyRqlFilter(signal))
                .anyMatch(applyNamespaceFilter(signal));
    }

    private static Predicate<FilteredTopic> applyTopicFilter(final Signal<?> signal) {
        return t -> t.getTopic().equals(topicFromSignal(signal).orElse(null));
    }

    private static Predicate<FilteredTopic> applyNamespaceFilter(final WithId signal) {
        return t -> t.getNamespaces().isEmpty() || t.getNamespaces().contains(namespaceFromId(signal));
    }

    private static Predicate<FilteredTopic> applyRqlFilter(final Signal<?> signal) {
        return t -> !t.hasFilter() || t.getFilter().filter(f -> matchesFilter(f, signal)).isPresent();
    }

    private static String namespaceFromId(final WithId withId) {
        return withId.getId().split(":", 2)[0];
    }

    private static boolean matchesFilter(final String filter, final Signal<?> signal) {

        if (signal instanceof ThingEvent) {

            // currently only ThingEvents may be filtered
            return ThingEventToThingConverter.thingEventToThing((ThingEvent) signal)
                    .filter(thing -> ThingPredicateVisitor.apply(parseCriteria(filter))
                            .test(thing)
                    )
                    .isPresent();
        } else {
            return true;
        }
    }

    private static Criteria parseCriteria(final String filter) {

        final CriteriaFactory criteriaFactory = new CriteriaFactoryImpl();
        final ThingsFieldExpressionFactory fieldExpressionFactory =
                new ModelBasedThingsFieldExpressionFactory();
        final QueryFilterCriteriaFactory queryFilterCriteriaFactory =
                new QueryFilterCriteriaFactory(criteriaFactory, fieldExpressionFactory);

        return queryFilterCriteriaFactory.filterCriteria(filter, DittoHeaders.empty());
    }

    private static boolean isTargetAuthorized(final Target target, final Signal<?> signal) {
        final Set<String> authorizedReadSubjects = signal.getDittoHeaders().getReadSubjects();
        final AuthorizationContext authorizationContext = target.getAuthorizationContext();
        final List<String> connectionSubjects = authorizationContext.getAuthorizationSubjectIds();
        return !Collections.disjoint(authorizedReadSubjects, connectionSubjects);
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

    private SignalFilter() {
    }
}
