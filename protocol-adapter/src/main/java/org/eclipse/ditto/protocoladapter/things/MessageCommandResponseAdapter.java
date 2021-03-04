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

import org.eclipse.ditto.model.messages.KnownMessageSubjects;
import org.eclipse.ditto.protocoladapter.AbstractAdapter;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.adaptables.MappingStrategiesFactory;
import org.eclipse.ditto.protocoladapter.signals.SignalMapper;
import org.eclipse.ditto.protocoladapter.signals.SignalMapperFactory;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessageResponse;
import org.eclipse.ditto.signals.commands.messages.SendFeatureMessageResponse;
import org.eclipse.ditto.signals.commands.messages.SendThingMessageResponse;

/**
 * Adapter for mapping a {@link MessageCommandResponseAdapter} to and from an {@link Adaptable}.
 */
final class MessageCommandResponseAdapter extends AbstractAdapter<MessageCommandResponse<?, ?>>
        implements ThingMessageAdapter<MessageCommandResponse<?, ?>> {

    private static final SignalMapper<MessageCommandResponse<?, ?>>
            TO_ADAPTABLE_MAPPER = SignalMapperFactory.newMessageCommandResponseSignalMapper();

    private MessageCommandResponseAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getMessageCommandResponseMappingStrategies(), headerTranslator,
                EmptyPathMatcher.getInstance());
    }

    /**
     * Returns a new MessageCommandResponseAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static MessageCommandResponseAdapter of(final HeaderTranslator headerTranslator) {
        return new MessageCommandResponseAdapter(requireNonNull(headerTranslator));
    }

    @Override
    public Adaptable toAdaptable(final MessageCommandResponse<?, ?> t) {
        return toAdaptable(t, TopicPath.Channel.LIVE);
    }

    @Override
    public boolean isForResponses() {
        return true;
    }

    @Override
    public boolean requiresSubject() {
        return true;
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        if (adaptable.getTopicPath().getSubject().filter(KnownMessageSubjects.CLAIM_SUBJECT::equals).isPresent()) {
            return SendClaimMessageResponse.TYPE;
        } else if (adaptable.getPayload().getPath().getFeatureId().isPresent()) {
            return SendFeatureMessageResponse.TYPE;
        } else {
            return SendThingMessageResponse.TYPE;
        }
    }

    @Override
    public Adaptable mapSignalToAdaptable(final MessageCommandResponse<?, ?> command, final TopicPath.Channel channel) {
        return TO_ADAPTABLE_MAPPER.mapSignalToAdaptable(command, channel);
    }
}
