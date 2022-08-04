/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.ErrorRegistry;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.mapper.SignalMapperFactory;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategiesFactory;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionEvent;

/**
 * Adapter for mapping a {@link SubscriptionEvent} to and from an {@link Adaptable}.
 */
final class SubscriptionEventAdapter extends AbstractThingAdapter<SubscriptionEvent<?>> {

    private SubscriptionEventAdapter(final HeaderTranslator headerTranslator,
            final ErrorRegistry<?> errorRegistry) {
        super(MappingStrategiesFactory.getSubscriptionEventMappingStrategies(errorRegistry),
                SignalMapperFactory.newSubscriptionEventSignalMapper(),
                headerTranslator);
    }

    /**
     * Returns a new ThingEventAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @param errorRegistry the error registry for {@code SubscriptionFailed} events.
     * @return the adapter.
     *
     * @since 1.1.0
     */
    public static SubscriptionEventAdapter of(final HeaderTranslator headerTranslator,
            final ErrorRegistry<?> errorRegistry) {
        return new SubscriptionEventAdapter(checkNotNull(headerTranslator, "headerTranslator"),
                checkNotNull(errorRegistry, "errorRegistry"));
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        return SubscriptionEvent.TYPE_PREFIX + adaptable.getTopicPath().getSearchAction().orElse(null);
    }

    @Override
    public Set<TopicPath.Criterion> getCriteria() {
        return EnumSet.of(TopicPath.Criterion.SEARCH);
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
    public Set<TopicPath.SearchAction> getSearchActions() {
        return EnumSet.of(TopicPath.SearchAction.COMPLETE, TopicPath.SearchAction.NEXT,
                TopicPath.SearchAction.FAILED, TopicPath.SearchAction.GENERATED);
    }
}
