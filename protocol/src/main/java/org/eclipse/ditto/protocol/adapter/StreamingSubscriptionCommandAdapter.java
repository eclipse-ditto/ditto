/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.commands.streaming.StreamingSubscriptionCommand;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.mapper.SignalMapperFactory;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategiesFactory;

/**
 * Adapter for mapping a {@link StreamingSubscriptionCommand} to and from an {@link Adaptable}.
 *
 * @since 3.2.0
 */
public final class StreamingSubscriptionCommandAdapter
        extends AbstractStreamingMessageAdapter<StreamingSubscriptionCommand<?>> {

    private StreamingSubscriptionCommandAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getStreamingSubscriptionCommandMappingStrategies(),
                SignalMapperFactory.newStreamingSubscriptionCommandSignalMapper(),
                headerTranslator
        );
    }

    /**
     * Returns a new StreamingSubscriptionCommandAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static StreamingSubscriptionCommandAdapter of(final HeaderTranslator headerTranslator) {
        return new StreamingSubscriptionCommandAdapter(checkNotNull(headerTranslator, "headerTranslator"));
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        return StreamingSubscriptionCommand.TYPE_PREFIX + adaptable.getTopicPath().getStreamingAction().orElse(null);
    }

    @Override
    public Set<TopicPath.Criterion> getCriteria() {
        return EnumSet.of(TopicPath.Criterion.STREAMING);
    }

    @Override
    public Set<TopicPath.Action> getActions() {
        return Collections.emptySet();
    }

    @Override
    public boolean isForResponses() {
        return false;
    }

    @Override
    public Set<TopicPath.StreamingAction> getStreamingActions() {
        return EnumSet.of(TopicPath.StreamingAction.SUBSCRIBE_FOR_PERSISTED_EVENTS, TopicPath.StreamingAction.REQUEST,
                TopicPath.StreamingAction.CANCEL);
    }
}
