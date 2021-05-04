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

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.HeaderTranslator;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.UnknownEventException;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategiesFactory;
import org.eclipse.ditto.base.model.signals.ErrorRegistry;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionComplete;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionCreated;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionEvent;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionFailed;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionHasNextPage;

/**
 * Adapter for mapping a {@link org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionEvent} to and from an {@link Adaptable}.
 */
final class SubscriptionEventAdapter extends AbstractThingAdapter<SubscriptionEvent<?>> {

    private SubscriptionEventAdapter(final HeaderTranslator headerTranslator,
            final ErrorRegistry<?> errorRegistry) {
        super(MappingStrategiesFactory.getSubscriptionEventMappingStrategies(errorRegistry), headerTranslator);
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
    protected Adaptable mapSignalToAdaptable(final SubscriptionEvent<?> event, final TopicPath.Channel channel) {
        TopicPath topicPath;
        final PayloadBuilder payloadBuilder = Payload.newBuilder(event.getResourcePath());
        final JsonObjectBuilder payloadContentBuilder = JsonFactory.newObjectBuilder();
        final JsonFieldDefinition<String> subscriptionIdKey = SubscriptionEvent.JsonFields.SUBSCRIPTION_ID;

        if (event instanceof SubscriptionCreated) {
            topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER).things().twin().search().generated().build();
            SubscriptionCreated createdEvent = (SubscriptionCreated) event;
            payloadContentBuilder.set(subscriptionIdKey, createdEvent.getSubscriptionId());

        } else if (event instanceof SubscriptionComplete) {
            topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER).things().twin().search().complete().build();
            SubscriptionComplete completedEvent = (SubscriptionComplete) event;
            payloadContentBuilder.set(subscriptionIdKey, completedEvent.getSubscriptionId());

        } else if (event instanceof SubscriptionFailed) {
            topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER).things().twin().search().failed().build();
            SubscriptionFailed failedEvent = (SubscriptionFailed) event;
            payloadContentBuilder
                    .set(subscriptionIdKey, failedEvent.getSubscriptionId())
                    .set(SubscriptionFailed.JsonFields.ERROR, failedEvent.getError().toJson());

        } else if (event instanceof SubscriptionHasNextPage) {
            topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER).things().twin().search().hasNext().build();
            SubscriptionHasNextPage hasNextEvent = (SubscriptionHasNextPage) event;
            payloadContentBuilder
                    .set(subscriptionIdKey, hasNextEvent.getSubscriptionId())
                    .set(SubscriptionHasNextPage.JsonFields.ITEMS, hasNextEvent.getItems());

        } else {
            throw UnknownEventException.newBuilder(event.getClass().getCanonicalName()).build();
        }

        return Adaptable.newBuilder(topicPath)
                .withPayload(payloadBuilder.withValue(payloadContentBuilder.build()).build())
                .withHeaders(ProtocolFactory.newHeadersWithJsonContentType(event.getDittoHeaders()))
                .build();
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
