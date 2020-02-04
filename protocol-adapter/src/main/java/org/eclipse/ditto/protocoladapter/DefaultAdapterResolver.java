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
package org.eclipse.ditto.protocoladapter;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.protocoladapter.policies.DefaultPolicyCommandAdapterProvider;
import org.eclipse.ditto.protocoladapter.provider.AdapterProvider;
import org.eclipse.ditto.protocoladapter.provider.PolicyCommandAdapterProvider;
import org.eclipse.ditto.protocoladapter.provider.ThingCommandAdapterProvider;
import org.eclipse.ditto.protocoladapter.things.DefaultThingCommandAdapterProvider;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Implements the logic to select the correct {@link Adapter} from a given {@link Adaptable}.
 */
final class DefaultAdapterResolver implements AdapterResolver {

    private final ThingCommandAdapterProvider thingsAdapters;
    private final PolicyCommandAdapterProvider policyCommandAdapters;

    public DefaultAdapterResolver(DefaultThingCommandAdapterProvider thingsAdapters,
            DefaultPolicyCommandAdapterProvider policiesAdapters) {
        this.thingsAdapters = thingsAdapters;
        this.policyCommandAdapters = policiesAdapters;
    }

    @Override
    public Adapter<? extends Signal<?>> getAdapter(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final Adapter<? extends Signal<?>> adapter;
        if (TopicPath.Group.THINGS.equals(topicPath.getGroup())) { // /things
            adapter = getAdapter(adaptable, thingsAdapters);
        } else if (TopicPath.Group.POLICIES.equals(topicPath.getGroup())) {  // /policies
            adapter = getAdapter(adaptable, policyCommandAdapters);
        } else {
            throw UnknownTopicPathException.newBuilder(topicPath).build();
        }
        return adapter;
    }

    public Adapter<? extends Signal<?>> getAdapter(final Adaptable adaptable, final AdapterProvider adapterProvider) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final TopicPath.Channel channel = topicPath.getChannel();
        final Optional<Adapter<? extends Signal<?>>> adapter;
        if (TopicPath.Channel.LIVE.equals(channel)) { // /<group>/live
            adapter = Optional.ofNullable(fromLiveAdaptable(adaptable, adapterProvider));
        } else if (TopicPath.Channel.TWIN.equals(channel)) { // /<group>/twin
            adapter = Optional.ofNullable(fromTwinAdaptable(adaptable, adapterProvider));
        } else {
            adapter = Optional.empty();
        }
        return adapter.orElseThrow(() -> UnknownTopicPathException.newBuilder(topicPath).build());
    }

    private Adapter<? extends Signal<?>> fromLiveAdaptable(final Adaptable adaptable,
            final AdapterProvider adapterProvider) {
        final TopicPath topicPath = adaptable.getTopicPath();

        if (TopicPath.Criterion.MESSAGES.equals(topicPath.getCriterion())) { // /<group>/live/messages
            final boolean isResponse = isResponse(adaptable);
            if (isResponse) {
                return adapterProvider.getMessageCommandResponseAdapter();
            } else {
                return adapterProvider.getMessageCommandAdapter();
            }
        } else {
            return signalFromThingAdaptable(adaptable, adapterProvider); // /<group>/live/(commands|events)
        }
    }

    private Adapter<? extends Signal<?>> fromTwinAdaptable(final Adaptable adaptable,
            final AdapterProvider adapterProvider) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return signalFromThingAdaptable(adaptable, adapterProvider); // /<group>/twin/(commands|events)
    }

    @Nullable
    private Adapter<? extends Signal<?>> signalFromThingAdaptable(final Adaptable adaptable,
            final AdapterProvider adapterProvider) {
        final TopicPath topicPath = adaptable.getTopicPath();
        if (TopicPath.Criterion.COMMANDS.equals(topicPath.getCriterion())) {

            if (isResponse(adaptable)) {
                // this was a command response:
                return processCommandResponseSignalFromAdaptable(adaptable, adapterProvider);
            } else if (TopicPath.Action.RETRIEVE.equals(topicPath.getAction().orElse(null))) {
                return adapterProvider.getQueryCommandAdapter();
            } else {
                return adapterProvider.getModifyCommandAdapter();
            }

        } else if (TopicPath.Criterion.EVENTS.equals(topicPath.getCriterion())) {
            return adapterProvider.getEventAdapter();
        } else if (TopicPath.Criterion.ERRORS.equals(topicPath.getCriterion())) {
            return adapterProvider.getErrorResponseAdapter();
        }
        return null;
    }

    private Adapter<? extends Signal<?>> processCommandResponseSignalFromAdaptable(final Adaptable adaptable,
            final AdapterProvider adapterProvider) {
        final TopicPath topicPath = adaptable.getTopicPath();
        if (isErrorResponse(adaptable)) {
            return adapterProvider.getErrorResponseAdapter();
        } else if (TopicPath.Action.RETRIEVE.equals(topicPath.getAction().orElse(null))) {
            return adapterProvider.getQueryCommandResponseAdapter();
        } else {
            return adapterProvider.getModifyCommandResponseAdapter();
        }
    }

    private static boolean isResponse(final Adaptable adaptable) {
        return adaptable.getPayload().getStatus().isPresent();
    }

    private static boolean isErrorResponse(final Adaptable adaptable) {
        final Optional<HttpStatusCode> status = adaptable.getPayload().getStatus();
        return status.isPresent() && status.get().toInt() >= HttpStatusCode.BAD_REQUEST.toInt();
    }

}