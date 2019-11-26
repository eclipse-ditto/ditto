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
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * Base class for {@link AdapterResolver} implementations. Provides the logic to select the correct {@link Adapter}
 * implementation which are provided by subclasses of this class.
 */
abstract class AbstractAdapterResolver implements AdapterResolver {

    private final Adapter<? extends Signal> queryCommandAdapter;
    private final Adapter<? extends CommandResponse> queryCommandResponseAdapter;
    private final Adapter<? extends Signal> modifyCommandAdapter;
    private final Adapter<? extends CommandResponse> modifyCommandResponseAdapter;
    private final Adapter<? extends MessageCommand> messageCommandAdapter;
    private final Adapter<? extends MessageCommandResponse> messageCommandResponseAdapter;
    private final Adapter<? extends Event<?>> eventAdapter;
    private final Adapter<? extends ErrorResponse> errorResponseAdapter;

    AbstractAdapterResolver(final Adapter<? extends Signal> queryCommandAdapter,
            final Adapter<? extends CommandResponse> queryCommandResponseAdapter,
            final Adapter<? extends Signal> modifyCommandAdapter,
            final Adapter<? extends CommandResponse> modifyCommandResponseAdapter,
            final Adapter<? extends MessageCommand> messageCommandAdapter,
            final Adapter<? extends MessageCommandResponse> messageCommandResponseAdapter,
            final Adapter<? extends Event<?>> eventAdapter,
            final Adapter<? extends ErrorResponse> errorResponseAdapter) {
        this.queryCommandAdapter = queryCommandAdapter;
        this.queryCommandResponseAdapter = queryCommandResponseAdapter;
        this.modifyCommandAdapter = modifyCommandAdapter;
        this.modifyCommandResponseAdapter = modifyCommandResponseAdapter;
        this.messageCommandAdapter = messageCommandAdapter;
        this.messageCommandResponseAdapter = messageCommandResponseAdapter;
        this.eventAdapter = eventAdapter;
        this.errorResponseAdapter = errorResponseAdapter;
    }

    @Override
    public Adapter<? extends Signal> getQueryCommandAdapter() {
        return queryCommandAdapter;
    }

    @Override
    public Adapter<? extends CommandResponse> getQueryCommandResponseAdapter() {
        return queryCommandResponseAdapter;
    }

    @Override
    public Adapter<? extends Signal> getModifyCommandAdapter() {
        return modifyCommandAdapter;
    }

    @Override
    public Adapter<? extends CommandResponse> getModifyCommandResponseAdapter() {
        return modifyCommandResponseAdapter;
    }

    @Override
    public Adapter<? extends MessageCommand> getMessageCommandAdapter() {
        return messageCommandAdapter;
    }

    @Override
    public Adapter<? extends Signal> getMessageCommandResponseAdapter() {
        return messageCommandResponseAdapter;
    }

    @Override
    public Adapter<? extends Event<?>> getEventAdapter() {
        return eventAdapter;
    }

    @Override
    public Adapter<? extends ErrorResponse> getErrorResponseAdapter() {
        return errorResponseAdapter;
    }

    @Override
    public Adapter<? extends Signal> getAdapter(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final TopicPath.Channel channel = topicPath.getChannel();
        final Optional<Adapter<? extends Signal>> adapter;
        if (TopicPath.Channel.LIVE.equals(channel)) { // /<group>/live
            adapter = Optional.ofNullable(fromLiveAdaptable(adaptable));
        } else if (TopicPath.Channel.TWIN.equals(channel)) { // /<group>/twin
            adapter = Optional.ofNullable(fromTwinAdaptable(adaptable));
        } else {
            adapter = Optional.empty();
        }
        return adapter.orElseThrow(() -> UnknownTopicPathException.newBuilder(topicPath).build());
    }

    private Adapter<? extends Signal> fromLiveAdaptable(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();

        if (TopicPath.Criterion.MESSAGES.equals(topicPath.getCriterion())) { // /<group>/live/messages
            final boolean isResponse = isResponse(adaptable);
            if (isResponse) {
                return getMessageCommandResponseAdapter();
            } else {
                return getMessageCommandAdapter();
            }
        } else {
            return signalFromThingAdaptable(adaptable, topicPath); // /<group>/live/(commands|events)
        }
    }

    private Adapter<? extends Signal> fromTwinAdaptable(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return signalFromThingAdaptable(adaptable, topicPath); // /<group>/twin/(commands|events)
    }

    @Nullable
    private Adapter<? extends Signal> signalFromThingAdaptable(
            final Adaptable adaptable,
            final TopicPath topicPath) {
        if (TopicPath.Criterion.COMMANDS.equals(topicPath.getCriterion())) {

            if (isResponse(adaptable)) {
                // this was a command response:
                return processCommandResponseSignalFromAdaptable(adaptable, topicPath);
            } else if (TopicPath.Action.RETRIEVE.equals(topicPath.getAction().orElse(null))) {
                return getQueryCommandAdapter();
            } else {
                return getModifyCommandAdapter();
            }

        } else if (TopicPath.Criterion.EVENTS.equals(topicPath.getCriterion())) {
            return getEventAdapter();
        } else if (TopicPath.Criterion.ERRORS.equals(topicPath.getCriterion())) {
            return getErrorResponseAdapter();
        }
        return null;
    }

    private Adapter<? extends Signal> processCommandResponseSignalFromAdaptable(final Adaptable adaptable,
            final TopicPath topicPath) {
        final boolean errorResponse = isErrorResponse(adaptable);
        if (TopicPath.Action.RETRIEVE.equals(topicPath.getAction().orElse(null))) {
            return errorResponse ? getErrorResponseAdapter() : getQueryCommandResponseAdapter();
        } else {
            return errorResponse ? getErrorResponseAdapter() : getModifyCommandResponseAdapter();
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