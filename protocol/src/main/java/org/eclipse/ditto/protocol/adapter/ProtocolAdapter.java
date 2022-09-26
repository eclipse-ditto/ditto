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
package org.eclipse.ditto.protocol.adapter;

import static org.eclipse.ditto.protocol.TopicPath.Channel.LIVE;
import static org.eclipse.ditto.protocol.TopicPath.Channel.NONE;
import static org.eclipse.ditto.protocol.TopicPath.Channel.TWIN;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.announcements.Announcement;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandResponse;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;

/**
 * A protocol adapter provides methods for mapping {@link Signal} instances to an {@link org.eclipse.ditto.protocol.Adaptable}.
 */
public interface ProtocolAdapter {

    /**
     * Maps the given {@code Adaptable} to the corresponding {@code Signal}, which can be a {@code Command},
     * {@code CommandResponse} or an {@code Event}.
     *
     * @param adaptable the adaptable.
     * @return the Signal.
     */
    Signal<?> fromAdaptable(Adaptable adaptable);

    /**
     * Maps the given {@code Signal} to an {@code Adaptable}.
     *
     * @param signal the signal.
     * @return the adaptable.
     * @throws org.eclipse.ditto.protocol.UnknownSignalException if the passed Signal was not supported by the ProtocolAdapter
     */
    Adaptable toAdaptable(Signal<?> signal);

    /**
     * Maps the given {@code Signal} to an {@code Adaptable}.
     *
     * @param signal the signal.
     * @param channel the channel to use when converting toAdaptable. This will overwrite any channel header in {@code signal}.
     * @return the adaptable.
     * @throws org.eclipse.ditto.protocol.UnknownSignalException if the passed Signal was not supported by the ProtocolAdapter
     * @since 1.1.0
     */
    Adaptable toAdaptable(Signal<?> signal, TopicPath.Channel channel);

    /**
     * Maps the given {@code Signal} to its {@code TopicPath}.
     *
     * @param signal the signal.
     * @return the topic path.
     * @since 2.2.0
     */
    TopicPath toTopicPath(Signal<?> signal);

    /**
     * Retrieve the header translator responsible for this protocol adapter.
     *
     * @return the header translator.
     */
    HeaderTranslator headerTranslator();

    /**
     * Test whether a signal belongs to the live channel.
     *
     * @param signal the signal.
     * @return whether it is a live signal.
     */
    static boolean isLiveSignal(final Signal<?> signal) {
        return signal.getDittoHeaders()
                .getChannel()
                .filter(TopicPath.Channel.LIVE.getName()::equals)
                .isPresent();
    }

    /**
     * Determine the channel of the processed {@link Signal}. First the DittoHeaders are checked for the
     * {@link org.eclipse.ditto.base.model.headers.DittoHeaderDefinition#CHANNEL} header. If not given the default
     * channel is determined by the type of the {@link Signal}.
     *
     * @param signal the processed signal
     * @return the channel determined from the signal
     */
    static TopicPath.Channel determineChannel(final Signal<?> signal) {
        // internally a twin command/event and live command/event are distinguished only  by the channel header i.e.
        // a twin and live command "look the same" except for the channel header
        final boolean isLiveSignal = isLiveSignal(signal);
        return isLiveSignal ? LIVE  // live signals (live commands/events) use the live channel
                : determineDefaultChannel(signal); // use default for other commands
    }

    /**
     * Determines the default channel of the processed {@link Signal} by signal type.
     *
     * @param signal the processed signal
     * @return the default channel determined from the signal
     */
    static TopicPath.Channel determineDefaultChannel(final Signal<?> signal) {
        if (signal instanceof PolicyCommand || signal instanceof PolicyCommandResponse ||
                signal instanceof PolicyEvent) {
            return NONE;
        } else if (signal instanceof ConnectivityCommand || signal instanceof ConnectivityCommandResponse ||
                signal instanceof ConnectivityEvent) {
            return NONE;
        } else if (signal instanceof Announcement) {
            return NONE;
        } else if (signal instanceof MessageCommand || signal instanceof MessageCommandResponse) {
            return LIVE;
        } else {
            return TWIN;
        }
    }

}
