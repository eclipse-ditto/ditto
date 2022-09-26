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
import org.eclipse.ditto.base.model.signals.ErrorRegistry;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionEvent;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.mapper.SignalMapperFactory;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategiesFactory;

/**
 * Adapter for mapping a {@link StreamingSubscriptionEvent} to and from an {@link Adaptable}.
 *
 * @since 3.2.0
 */
public final class StreamingSubscriptionEventAdapter
        extends AbstractStreamingMessageAdapter<StreamingSubscriptionEvent<?>> {

    private StreamingSubscriptionEventAdapter(final HeaderTranslator headerTranslator,
            final ErrorRegistry<?> errorRegistry) {
        super(MappingStrategiesFactory.getStreamingSubscriptionEventMappingStrategies(errorRegistry),
                SignalMapperFactory.newStreamingSubscriptionEventSignalMapper(),
                headerTranslator
        );
    }

    /**
     * Returns a new StreamingSubscriptionEventAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @param errorRegistry the error registry for {@code SubscriptionFailed} events.
     * @return the adapter.
     */
    public static StreamingSubscriptionEventAdapter of(final HeaderTranslator headerTranslator,
            final ErrorRegistry<?> errorRegistry) {
        return new StreamingSubscriptionEventAdapter(checkNotNull(headerTranslator, "headerTranslator"),
                checkNotNull(errorRegistry, "errorRegistry"));
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        return StreamingSubscriptionEvent.TYPE_PREFIX + adaptable.getTopicPath().getStreamingAction().orElse(null);
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
        return EnumSet.of(TopicPath.StreamingAction.COMPLETE, TopicPath.StreamingAction.NEXT,
                TopicPath.StreamingAction.FAILED, TopicPath.StreamingAction.GENERATED);
    }

    @Override
    public boolean supportsWildcardTopics() {
        return false;
    }
}
