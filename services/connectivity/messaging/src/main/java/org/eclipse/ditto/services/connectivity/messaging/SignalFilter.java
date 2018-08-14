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
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithThingId;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * Filters a set of targets by
 * <ul>
 * <li>removing those targets that do not want to receive a signal</li>
 * <li>removing those targets that are not allowed to read a signal</li>
 * </ul>
 */
public class SignalFilter {


    public static Set<Target> filter(final Connection connection, final Signal<?> signal) {
        final Topic topic = topicFromSignal(signal).orElse(null);
        return connection.getTargets().stream()
                .filter(t -> isTargetSubscribedForTopic(t, topic))
                .filter(t -> isTargetAuthorized(t, signal))
                .collect(Collectors.toSet());
    }

    private static boolean isTargetSubscribedForTopic(final Target t, @Nullable final Topic topicFromSignal) {
        return t.getTopics().contains(topicFromSignal);
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
