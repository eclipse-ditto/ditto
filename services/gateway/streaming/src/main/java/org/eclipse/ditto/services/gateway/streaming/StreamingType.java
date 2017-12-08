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
package org.eclipse.ditto.services.gateway.streaming;

import java.util.Arrays;

import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * Enumeration of the different types which can be streamed in the Gateway (e.g. to an open Websocket connection). Each
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
