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
package org.eclipse.ditto.services.models.concierge.streaming;

import java.util.Arrays;

import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * Enumeration of the different types which can be streamed (e.g. to an open Websocket connection). Each
 * type is also aware of the key used for distributed pub/sub in the Akka cluster.
 */
public enum StreamingType {

    EVENTS(ThingEvent.TYPE_PREFIX),
    MESSAGES(MessageCommand.TYPE_PREFIX),
    LIVE_COMMANDS("things-live-commands"),
    LIVE_EVENTS("things-live-events");

    private final String distributedPubSubTopic;

    StreamingType(final String distributedPubSubTopic) {
        this.distributedPubSubTopic = distributedPubSubTopic;
    }

    /**
     * @return the key used for distributed pub/sub in the Akka cluster for this StreamingType.
     */
    public String getDistributedPubSubTopic() {
        return distributedPubSubTopic;
    }

    /**
     * Returns a {@code StreamingType} from a given {@code distributedPubSubTopic} representation.
     *
     * @param distributedPubSubTopic the string representation of the topic.
     * @return the StreamingType.
     */
    public static StreamingType fromTopic(final String distributedPubSubTopic) {
        return Arrays.stream(values())
                .filter(header -> distributedPubSubTopic.equals(header.getDistributedPubSubTopic()))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException("Unknown distributedPubSubTopic: " + distributedPubSubTopic));
    }



}
