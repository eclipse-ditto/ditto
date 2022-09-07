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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import static org.eclipse.ditto.protocol.TopicPath.Criterion.COMMANDS;
import static org.eclipse.ditto.protocol.TopicPath.Criterion.EVENTS;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.namespaces.NamespaceReader;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithResource;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.FilteredTopic;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectivityAnnouncement;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitorRegistry;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.PlaceholderResolver;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.placeholders.ResourcePlaceholder;
import org.eclipse.ditto.protocol.placeholders.TopicPathPlaceholder;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingEventToThingConverter;

/**
 * Filters a set of targets by
 * <ul>
 * <li>removing those targets that do not want to receive a signal</li>
 * <li>removing those targets that are not allowed to read a signal</li>
 * </ul>
 */
public final class SignalFilter {

    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    private static final TopicPathPlaceholder TOPIC_PATH_PLACEHOLDER = TopicPathPlaceholder.getInstance();
    private static final ResourcePlaceholder RESOURCE_PLACEHOLDER = ResourcePlaceholder.getInstance();
    private static final TimePlaceholder TIME_PLACEHOLDER = TimePlaceholder.getInstance();

    private final Connection connection;
    private final ConnectionMonitorRegistry<ConnectionMonitor> connectionMonitorRegistry;

    SignalFilter(final Connection connection,
            final ConnectionMonitorRegistry<ConnectionMonitor> connectionMonitorRegistry) {
        this.connection = connection;
        this.connectionMonitorRegistry = connectionMonitorRegistry;
    }

    /**
     * Constructs a new SignalFilter instance with the given {@code connection}.
     *
     * @param connection the connection to filter the signals on.
     * @param connectionMonitorRegistry the monitor registry.
     * @return the signal filter.
     */
    public static SignalFilter of(final Connection connection,
            final ConnectionMonitorRegistry<ConnectionMonitor> connectionMonitorRegistry) {
        return new SignalFilter(connection, connectionMonitorRegistry);
    }

    /**
     * Filters the passed {@code signal} by extracting those {@link Target}s which should receive the signal.
     * Fields are ignored if they occur as "extra targets" to be evaluated later after signal enrichment.
     *
     * @param signal the signal to filter / determine the {@link org.eclipse.ditto.connectivity.model.Target}s for
     * @return the determined Targets for the passed in {@code signal}
     * @throws org.eclipse.ditto.base.model.exceptions.InvalidRqlExpressionException if the optional filter string of a
     * Target cannot be mapped to a valid criterion
     */
    @SuppressWarnings("squid:S3864")
    public List<Target> filter(final Signal<?> signal) {
        return connection.getTargets().stream()
                .filter(t -> isTargetAuthorized(t, signal)) // this is cheaper, so check this first
                .filter(t -> isTargetSubscribedForTopicGenerally(t, signal))
                // count authorized targets which generally are interested in the topic (e.g. "live messages")
                .peek(authorizedTarget -> connectionMonitorRegistry.forOutboundDispatched(connection,
                        authorizedTarget.getAddress())
                        .success(signal))
                .filter(t -> isTargetSubscribedForTopicWithFiltering(t, signal))
                // count authorized + filtered targets
                .peek(filteredTarget -> connectionMonitorRegistry.forOutboundFiltered(connection,
                        filteredTarget.getAddress())
                        .success(signal))
                .toList();
    }

    private static boolean isTargetAuthorized(final Target target, final Signal<?> signal) {
        if (signal instanceof PolicyAnnouncement || signal instanceof ConnectivityAnnouncement) {
            return true;
        } else {
            final AuthorizationContext authorizationContext = target.getAuthorizationContext();
            final DittoHeaders headers = signal.getDittoHeaders();
            return authorizationContext.isAuthorized(headers.getReadGrantedSubjects(),
                    headers.getReadRevokedSubjects());
        }
    }

    private static boolean isTargetSubscribedForTopicGenerally(final Target target, final Signal<?> signal) {
        return target.getTopics().stream()
                .anyMatch(applyTopicFilter(signal));
    }

    private static boolean isTargetSubscribedForTopicWithFiltering(final Target target, final Signal<?> signal) {
        return target.getTopics().stream()
                .filter(applyTopicFilter(signal))
                .filter(applyNamespaceFilter(signal))
                .anyMatch(filteredTopic -> matchesFilterBeforeEnrichment(filteredTopic, signal));
    }

    private static Predicate<FilteredTopic> applyTopicFilter(final Signal<?> signal) {
        return t -> t.getTopic().equals(topicFromSignal(signal).orElse(null));
    }

    private static Predicate<FilteredTopic> applyNamespaceFilter(final Signal<?> signal) {
        return t -> t.getNamespaces().isEmpty() ||
                (signal instanceof WithEntityId withEntityId && t.getNamespaces().contains(namespaceFromId(withEntityId)));
    }

    @Nullable
    private static String namespaceFromId(final WithEntityId withEntityId) {
        return NamespaceReader.fromEntityId(withEntityId.getEntityId()).orElse(null);
    }

    private static boolean matchesFilterBeforeEnrichment(final FilteredTopic filteredTopic, final Signal<?> signal) {
        final Optional<String> filterOptional = filteredTopic.getFilter();
        if (filterOptional.isPresent()) {
            // match filter ignoring "extraFields"

            final TopicPath topicPath = DITTO_PROTOCOL_ADAPTER.toTopicPath(signal);
            final PlaceholderResolver<TopicPath> topicPathPlaceholderResolver =
                    PlaceholderFactory.newPlaceholderResolver(TOPIC_PATH_PLACEHOLDER, topicPath);
            final PlaceholderResolver<WithResource> resourcePlaceholderResolver = PlaceholderFactory
                    .newPlaceholderResolver(RESOURCE_PLACEHOLDER, signal);
            final PlaceholderResolver<Object> timePlaceholderResolver = PlaceholderFactory
                    .newPlaceholderResolver(TIME_PLACEHOLDER, new Object());
            final Criteria criteria = parseCriteria(filterOptional.get(), signal.getDittoHeaders(),
                    topicPathPlaceholderResolver, resourcePlaceholderResolver, timePlaceholderResolver);
            final Set<JsonPointer> extraFields = filteredTopic.getExtraFields()
                    .map(JsonFieldSelector::getPointers)
                    .orElse(Collections.emptySet());
            if (signal instanceof ThingEvent) {
                return ThingEventToThingConverter.thingEventToThing((ThingEvent<?>) signal)
                        .filter(thing -> Thing3ValuePredicateVisitor.couldBeTrue(criteria, extraFields, thing,
                                topicPathPlaceholderResolver, resourcePlaceholderResolver, timePlaceholderResolver))
                        .isPresent();
            } else {
                final Thing emptyThing = Thing.newBuilder().build();
                return Thing3ValuePredicateVisitor.couldBeTrue(criteria, extraFields, emptyThing,
                        topicPathPlaceholderResolver, resourcePlaceholderResolver, timePlaceholderResolver);
            }
        } else {
            return true;
        }
    }

    /**
     * @throws org.eclipse.ditto.base.model.exceptions.InvalidRqlExpressionException if the filter string cannot be
     * mapped to a valid criterion
     */
    private static Criteria parseCriteria(final String filter, final DittoHeaders dittoHeaders,
            final PlaceholderResolver<?>... placeholderResolvers) {
        return QueryFilterCriteriaFactory.modelBased(RqlPredicateParser.getInstance(), placeholderResolvers)
                .filterCriteria(filter, dittoHeaders);
    }

    private static Optional<Topic> topicFromSignal(final Signal<?> signal) {
        if (signal instanceof PolicyAnnouncement) {
            return Optional.of(Topic.POLICY_ANNOUNCEMENTS);
        }
        if (signal instanceof ConnectivityAnnouncement) {
            return Optional.of(Topic.CONNECTION_ANNOUNCEMENTS);
        }
        // check things group
        final TopicPath.Group group =
                WithEntityId.getEntityIdOfType(ThingId.class, signal).isPresent() ? TopicPath.Group.THINGS : null;
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
