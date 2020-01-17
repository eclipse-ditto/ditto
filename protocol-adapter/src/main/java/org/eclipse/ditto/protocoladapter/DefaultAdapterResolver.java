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
package org.eclipse.ditto.protocoladapter;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.protocoladapter.policies.DefaultPolicyCommandAdapters;
import org.eclipse.ditto.protocoladapter.things.DefaultThingCommandAdapters;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Base class for {@link AdapterResolver} implementations. Provides the logic to select the correct {@link Adapter}
 * implementation which are provided by subclasses of this class.
 */
public class DefaultAdapterResolver implements AdapterResolver {

    private final DefaultThingCommandAdapters thingsAdapters;
    private final PolicyCommandAdapters policyCommandAdapters;

    public DefaultAdapterResolver(DefaultThingCommandAdapters thingsAdapters,
            DefaultPolicyCommandAdapters policiesAdapters) {
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

    public Adapter<? extends Signal<?>> getAdapter(final Adaptable adaptable, final Adapters adapters) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final TopicPath.Channel channel = topicPath.getChannel();
        final Optional<Adapter<? extends Signal<?>>> adapter;
        if (TopicPath.Channel.LIVE.equals(channel)) { // /<group>/live
            adapter = Optional.ofNullable(fromLiveAdaptable(adaptable, adapters));
        } else if (TopicPath.Channel.TWIN.equals(channel)) { // /<group>/twin
            adapter = Optional.ofNullable(fromTwinAdaptable(adaptable, adapters));
        } else {
            adapter = Optional.empty();
        }
        return adapter.orElseThrow(() -> UnknownTopicPathException.newBuilder(topicPath).build());
    }

    private Adapter<? extends Signal<?>> fromLiveAdaptable(final Adaptable adaptable, final Adapters adapters) {
        final TopicPath topicPath = adaptable.getTopicPath();

        if (TopicPath.Criterion.MESSAGES.equals(topicPath.getCriterion())) { // /<group>/live/messages
            final boolean isResponse = isResponse(adaptable);
            if (isResponse) {
                return adapters.getMessageCommandResponseAdapter();
            } else {
                return adapters.getMessageCommandAdapter();
            }
        } else {
            return signalFromThingAdaptable(adaptable, adapters); // /<group>/live/(commands|events)
        }
    }

    private Adapter<? extends Signal<?>> fromTwinAdaptable(final Adaptable adaptable, final Adapters adapters) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return signalFromThingAdaptable(adaptable, adapters); // /<group>/twin/(commands|events)
    }

    @Nullable
    private Adapter<? extends Signal<?>> signalFromThingAdaptable(final Adaptable adaptable, final Adapters adapters) {
        final TopicPath topicPath = adaptable.getTopicPath();
        if (TopicPath.Criterion.COMMANDS.equals(topicPath.getCriterion())) {

            if (isResponse(adaptable)) {
                // this was a command response:
                return processCommandResponseSignalFromAdaptable(adaptable, adapters);
            } else if (TopicPath.Action.RETRIEVE.equals(topicPath.getAction().orElse(null))) {
                return adapters.getQueryCommandAdapter();
            } else {
                return adapters.getModifyCommandAdapter();
            }

        } else if (TopicPath.Criterion.EVENTS.equals(topicPath.getCriterion())) {
            return adapters.getEventAdapter();
        } else if (TopicPath.Criterion.ERRORS.equals(topicPath.getCriterion())) {
            return adapters.getErrorResponseAdapter();
        }
        return null;
    }

    private Adapter<? extends Signal<?>> processCommandResponseSignalFromAdaptable(final Adaptable adaptable,
            final Adapters adapters) {
        final TopicPath topicPath = adaptable.getTopicPath();
        if (isErrorResponse(adaptable)) {
            return adapters.getErrorResponseAdapter();
        } else if (TopicPath.Action.RETRIEVE.equals(topicPath.getAction().orElse(null))) {
            return adapters.getQueryCommandResponseAdapter();
        } else {
            return adapters.getModifyCommandResponseAdapter();
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