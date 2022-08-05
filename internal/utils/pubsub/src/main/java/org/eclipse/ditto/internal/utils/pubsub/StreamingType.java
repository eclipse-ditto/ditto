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
package org.eclipse.ditto.internal.utils.pubsub;

import java.util.Arrays;
import java.util.Optional;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;

/**
 * Enumeration of the different types which can be streamed (e.g. to an open Websocket connection). Each
 * type is also aware of the key used for distributed pub/sub in the Akka cluster.
 */
public enum StreamingType {

    /**
     * Streaming type of thing events.
     */
    EVENTS("things.events:"),

    /**
     * Streaming type of message commands. The pubsub topic must be equal to the type prefix of message commands.
     */
    MESSAGES("messages.commands:"),

    /**
     * Streaming type of live commands.
     */
    LIVE_COMMANDS("things-live-commands"),

    /**
     * Streaming type of live events.
     */
    LIVE_EVENTS("things-live-events"),

    /**
     * Streaming type of policy announcements.
     *
     * @since 2.0.0
     */
    POLICY_ANNOUNCEMENTS("policy-announcements");

    /**
     * Equal to TopicPath.Channel.LIVE.getName().
     * Not referencing TopicPath in order not to depend on ditto-protocol-adapter.
     */
    private static final String LIVE_CHANNEL_NAME = "live";

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

    /**
     * Test whether a signal belongs to the live channel.
     *
     * @param signal the signal.
     * @return whether it is a live signal.
     */
    public static boolean isLiveSignal(final Signal<?> signal) {
        return signal.getDittoHeaders().getChannel().filter(LIVE_CHANNEL_NAME::equals).isPresent();
    }

    /**
     * Get the approximate streaming type of a signal as far as it can be discerned.
     *
     * @param signal the signal.
     * @return the streaming type most appropriate for the signal.
     */
    public static Optional<StreamingType> fromSignal(final Signal<?> signal) {
        final StreamingType result;
        final boolean isThingEvent = signal.getType().startsWith(EVENTS.getDistributedPubSubTopic());
        if (isLiveSignal(signal)) {
            if (isThingEvent) {
                result = LIVE_EVENTS;
            } else if (signal.getType().startsWith(MESSAGES.getDistributedPubSubTopic())) {
                result = MESSAGES;
            } else if (signal instanceof Command<?>) {
                result = LIVE_COMMANDS;
            } else {
                result = null;
            }
        } else if (isThingEvent) {
            result = EVENTS;
        } else {
            result = null;
        }
        return Optional.ofNullable(result);
    }

}
