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
package org.eclipse.ditto.protocol.adapter.things;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.messages.model.KnownMessageSubjects;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.EmptyPathMatcher;
import org.eclipse.ditto.protocol.mapper.SignalMapperFactory;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategiesFactory;

/**
 * Adapter for mapping a {@link MessageCommandAdapter} to and from an {@link Adaptable}.
 */
final class MessageCommandAdapter extends AbstractMessageAdapter<MessageCommand<?, ?>> {

    private MessageCommandAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getMessageCommandMappingStrategies(),
                SignalMapperFactory.newMessageCommandSignalMapper(),
                headerTranslator,
                EmptyPathMatcher.getInstance());
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
    public boolean isForResponses() {
        return false;
    }

    @Override
    public boolean requiresSubject() {
        return true;
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

}
