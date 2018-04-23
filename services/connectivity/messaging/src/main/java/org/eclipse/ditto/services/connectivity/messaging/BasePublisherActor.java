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

import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;

import akka.actor.AbstractActor;

/**
 * Base class for publisher actors. Holds the map of configured targets.
 *
 * @param <T> the type of targets for this actor
 */
public abstract class BasePublisherActor<T extends PublishTarget> extends AbstractActor {


    private static final String PATH_TEMPLATE = "_/_/{0}/{1}/{2}";

    private final Map<String, Set<T>> destinations = new HashMap<>();

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
                    .filter(TopicPathMapper.SUPPORTED_TOPICS::containsKey)
                    .forEach(topic -> destinations
                            .computeIfAbsent(topic, t -> new HashSet<>())
                            .add(publishTarget));
        });
    }

    protected Set<T> getDestinationForMessage(final ExternalMessage message) {
        return message.getTopicPath()
                .map(ProtocolFactory::newTopicPath)
                .map(topicFromMessage -> MessageFormat.format(PATH_TEMPLATE,
                        topicFromMessage.getGroup().getName(),
                        topicFromMessage.getChannel().getName(),
                        topicFromMessage.getCriterion().getName()))
                .map(destinations::get)
                .orElse(Collections.emptySet());
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
