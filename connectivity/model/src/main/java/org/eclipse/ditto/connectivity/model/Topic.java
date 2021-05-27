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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Optional;

/**
 * Defines the topics that are currently supported in a {@link Connection}.
 */
public enum Topic {

    TWIN_EVENTS("_/_/things/twin/events", "things.events:"),
    LIVE_MESSAGES("_/_/things/live/messages", "messages.commands:"),
    LIVE_EVENTS("_/_/things/live/events", "things-live-events"),
    LIVE_COMMANDS("_/_/things/live/commands", "things-live-commands"),

    /**
     * Connection target topic for policy announcements.
     *
     * @since 2.0.0
     */
    POLICY_ANNOUNCEMENTS("_/_/policies/announcements", "policy-announcements"),

    /**
     * Connection target topic for connection announcements.
     *
     * @since 2.1.0
     */
    CONNECTION_ANNOUNCEMENTS("_/_/connections/announcements", "connection-announcements");

    private final String name;
    private final String pubSubTopic;

    Topic(final String name, final String pubSubTopic) {
        this.pubSubTopic = pubSubTopic;
        this.name = name;
    }

    /**
     * @return the corresponding pubsub topic used to subscribe to events of this type in the akka cluster
     */
    public String getPubSubTopic() {
        return pubSubTopic;
    }

    /**
     * @return the name of the topic
     */
    public String getName() {
        return name;
    }

    /**
     * @param name name of the topic
     * @return the topic matching the given name
     */
    public static Optional<Topic> forName(final CharSequence name) {
        checkNotNull(name, "Name");
        return Arrays.stream(values())
                .filter(c -> c.name.contentEquals(name))
                .findFirst();
    }

    @Override
    public String toString() {
        return name;
    }
}
