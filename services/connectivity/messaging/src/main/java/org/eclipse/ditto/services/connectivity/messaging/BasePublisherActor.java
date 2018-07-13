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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.model.query.model.criteria.Criteria;
import org.eclipse.ditto.model.query.model.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.model.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.model.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.things.ModelBasedThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.things.ThingPredicateVisitor;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingEventToThingConverter;

import akka.actor.AbstractActor;

/**
 * Base class for publisher actors. Holds the map of configured targets.
 *
 * @param <T> the type of targets for this actor
 */
public abstract class BasePublisherActor<T extends PublishTarget> extends AbstractActor {


    private static final String PATH_TEMPLATE = "_/_/{0}/{1}/{2}";
    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

    private final Map<String, Set<T>> destinations = new HashMap<>();
    private final Map<String, Criteria> topicFilterCriteria = new HashMap<>();

    /**
     * Abstract constructor for creating abstract publisher actors.
     *
     * @param targets the {@link Target}s to publish to
     */
    protected BasePublisherActor(final Set<Target> targets) {
        checkNotNull(targets, "targets");

        // initialize a map with the configured topic and the targets where the messages should be sent to
        targets.forEach(target -> {
            final T publishTarget = toPublishTarget(target.getAddress());
            target.getTopics().stream()
                    .filter(topic -> TopicPathMapper.SUPPORTED_TOPICS.containsKey(topic.getPath()))
                    .forEach(topic -> {
                        destinations
                                .computeIfAbsent(topic.getPath(), t -> new HashSet<>())
                                .add(publishTarget);
                        topic.getFilter().ifPresent(filter ->
                                topicFilterCriteria.put(topic.getPath(), parseCriteria(filter))
                        );
                    });
        });
    }

    protected Set<T> getDestinationForMessage(final ExternalMessage message) {
        return message.getTopicPath()
                .map(ProtocolFactory::newTopicPath)
                .map(topicFromMessage -> MessageFormat.format(PATH_TEMPLATE,
                        topicFromMessage.getGroup().getName(),
                        topicFromMessage.getChannel().getName(),
                        topicFromMessage.getCriterion().getName()))
                .filter(topicPathString -> matchesFilter(topicPathString, message.getOriginatingAdaptable().orElse(null)))
                .map(destinations::get)
                .orElse(Collections.emptySet());
    }

    private boolean matchesFilter(final String topicPath, @Nullable final Adaptable originatingAdaptable) {

        if (originatingAdaptable == null) {
            return true;
        } else {
            final boolean hasFilterCriteria = topicFilterCriteria.containsKey(topicPath);

            final Signal<?> signal = DITTO_PROTOCOL_ADAPTER.fromAdaptable(originatingAdaptable);
            if (signal instanceof ThingEvent && hasFilterCriteria) {

                // currently only ThingEvents may be filtered
                return ThingEventToThingConverter.thingEventToThing((ThingEvent) signal)
                        .filter(thing ->
                                ThingPredicateVisitor.apply(topicFilterCriteria.get(topicPath))
                                        .test(thing)
                        )
                        .isPresent();
            } else {
                return true;
            }
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

    protected boolean isResponseOrError(final ExternalMessage message) {
        return (message.isResponse() || message.getTopicPath()
                .map(ProtocolFactory::newTopicPath)
                .map(TopicPath::getCriterion)
                .map(TopicPath.Criterion.ERRORS::equals)
                .orElse(false));
    }

    protected Map<String, Set<T>> getDestinations() {
        return Collections.unmodifiableMap(new HashMap<>(destinations));
    }

    protected abstract T toPublishTarget(final String address);

}
