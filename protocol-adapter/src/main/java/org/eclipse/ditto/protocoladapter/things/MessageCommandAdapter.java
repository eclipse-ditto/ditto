
/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter.things;

import static java.util.Objects.requireNonNull;

import java.util.Collections;

import org.eclipse.ditto.model.messages.KnownMessageSubjects;
import org.eclipse.ditto.protocoladapter.AbstractAdapter;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DefaultPayloadPathMatcher;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.adaptables.MappingStrategiesFactory;
import org.eclipse.ditto.protocoladapter.signals.SignalMapper;
import org.eclipse.ditto.protocoladapter.signals.SignalMapperFactory;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessage;
import org.eclipse.ditto.signals.commands.messages.SendFeatureMessage;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;

/**
 * Adapter for mapping a {@link MessageCommandAdapter} to and from an {@link Adaptable}.
 */
final class MessageCommandAdapter extends AbstractAdapter<MessageCommand<?, ?>>
        implements ThingMessageAdapter<MessageCommand<?, ?>> {

    private static final SignalMapper<MessageCommand<?, ?>>
            TO_ADAPTABLE_MAPPER = SignalMapperFactory.newMessageCommandSignalMapper();

    private MessageCommandAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getMessageCommandMappingStrategies(), headerTranslator,
                DefaultPayloadPathMatcher.from(Collections.emptyMap()));
    }

    /**
     * Returns a new MessageCommandAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static MessageCommandAdapter of(final HeaderTranslator headerTranslator) {
        return new MessageCommandAdapter(requireNonNull(headerTranslator));
    }

    @Override
    public Adaptable toAdaptable(final MessageCommand<?, ?> t) {
        return toAdaptable(t, TopicPath.Channel.LIVE);
    }

    @Override
    public boolean isForResponses() {
        return false;
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        if (adaptable.getTopicPath().getSubject().filter(KnownMessageSubjects.CLAIM_SUBJECT::equals).isPresent()) {
            return SendClaimMessage.TYPE;
        } else if (adaptable.getPayload().getPath().getFeatureId().isPresent()) {
            return SendFeatureMessage.TYPE;
        } else {
            return SendThingMessage.TYPE;
        }
    }

    @Override
    public Adaptable mapSignalToAdaptable(final MessageCommand<?, ?> command, final TopicPath.Channel channel) {
        return TO_ADAPTABLE_MAPPER.mapSignalToAdaptable(command, channel);
    }

}
