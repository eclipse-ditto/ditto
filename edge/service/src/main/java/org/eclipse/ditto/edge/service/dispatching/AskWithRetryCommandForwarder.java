/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.edge.service.dispatching;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.exceptions.AskException;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.edge.service.EdgeServiceTimeoutException;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.cacheloaders.AskWithRetry;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cacheloaders.config.DefaultAskWithRetryConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;
import akka.cluster.pubsub.DistributedPubSubMessage;
import akka.pattern.AskTimeoutException;

/**
 * Forwards commands from the edges to a specified ActorRef, waiting for a response if the command demands one.
 * Uses retry mechanism if the response doesn't arrive.
 */
public final class AskWithRetryCommandForwarder implements Extension {

    private static final ExtensionId EXTENSION_ID = new ExtensionId();

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(AskWithRetryCommandForwarder.class);

    private final ActorSystem system;
    private final AskWithRetryConfig askWithRetryConfig;

    public AskWithRetryCommandForwarder(final ActorSystem actorSystem) {
        system = actorSystem;
        askWithRetryConfig = DefaultAskWithRetryConfig.of(
                DefaultScopedConfig.dittoScoped(actorSystem.settings().config()),
                "ask-with-retry");
    }

    /**
     * Load the {@code AskWithRetryCommandForwarder}.
     *
     * @param actorSystem The actor system in which to load the forwarder.
     * @return The forwarder.
     */
    public static AskWithRetryCommandForwarder get(final ActorSystem actorSystem) {
        return EXTENSION_ID.get(actorSystem);
    }

    /**
     * Asks the given {@code receiver} for a response by telling {@code command}.
     * This method uses {@link AskWithRetry}. Forwards the response to the {@code sender}.
     *
     * @param command the command that is used to ask.
     * @param receiver the actor that should be asked.
     * @param sender the sender to which the response should be forwarded.
     */
    public void forwardCommand(final Command<?> command,
            final ActorRef receiver,
            final ActorRef sender) {

        if (shouldSendResponse(command.getDittoHeaders())) {
            AskWithRetry.askWithRetry(receiver, command, askWithRetryConfig, system, getResponseCaster(command))
                    .exceptionally(t -> handleException(t, sender))
                    .thenAccept(response -> handleResponse(response, sender));
        } else {
            receiver.tell(command, sender);
        }
    }

    /**
     * Asks the given {@code pubSubMediator} for a response by telling {@code message}.
     * This method uses {@link AskWithRetry}. Forwards the response to the {@code sender}.
     *
     * @param command the command that the message contains.
     * @param message the message that is used to ask.
     * @param pubSubMediator the pubSub mediator that should be asked
     * @param sender the sender to which the response should be forwarded.
     */
    public void forwardCommandViaPubSub(final Command<?> command,
            final DistributedPubSubMessage message,
            final ActorRef pubSubMediator,
            final ActorRef sender) {

        if (shouldSendResponse(command.getDittoHeaders())) {
            AskWithRetry.askWithRetry(pubSubMediator, message, askWithRetryConfig, system, getResponseCaster(command))
                    .exceptionally(t -> handleException(t, sender))
                    .thenAccept(response -> handleResponse(response, sender));
        } else {
            pubSubMediator.tell(message, sender);
        }
    }

    private boolean shouldSendResponse(final DittoHeaders dittoHeaders) {
        return dittoHeaders.isResponseRequired() ||
                needsTwinPersistedAcknowledgement(dittoHeaders) ||
                needsLiveResponseAcknowledgement(dittoHeaders);
    }

    private boolean needsTwinPersistedAcknowledgement(final DittoHeaders dittoHeaders) {
        return dittoHeaders.getAcknowledgementRequests()
                .stream()
                .anyMatch(ar -> DittoAcknowledgementLabel.TWIN_PERSISTED.equals(ar.getLabel()));
    }

    private boolean needsLiveResponseAcknowledgement(final DittoHeaders dittoHeaders) {
        return dittoHeaders.getAcknowledgementRequests()
                .stream()
                .anyMatch(ar -> DittoAcknowledgementLabel.LIVE_RESPONSE.equals(ar.getLabel()));
    }

    @Nullable
    private <T extends Signal<?>> T handleException(final Throwable t, final ActorRef sender) {
        if (t instanceof CompletionException && t.getCause() instanceof DittoRuntimeException) {
            sender.tell(t.getCause(), ActorRef.noSender());
        } else {
            throw (RuntimeException) t;
        }
        return null;
    }

    private <T extends Signal<?>> void handleResponse(@Nullable final T response, final ActorRef sender) {
        if (null != response) {
            LOGGER.withCorrelationId(response.getDittoHeaders()).debug("Forwarding response: {}", response);
            sender.tell(response, ActorRef.noSender());
        }
    }

    /**
     * Returns a mapping function, which casts an Object response to the command response class.
     *
     * @return the mapping function.
     */
    @SuppressWarnings("unchecked")
    private <R extends CommandResponse<?>> Function<Object, R> getResponseCaster(final Command<?> command) {
        return response -> {
            if (CommandResponse.class.isAssignableFrom(response.getClass())) {
                return (R) response;
            } else if (response instanceof AskException || response instanceof AskTimeoutException) {
                final Optional<DittoRuntimeException> dittoRuntimeException =
                        handleAskTimeoutForCommand(command, (Throwable) response);
                if (dittoRuntimeException.isPresent()) {
                    throw dittoRuntimeException.get();
                } else {
                    return null;
                }
            } else {
                throw reportErrorOrResponse(command, response);
            }
        };
    }

    /**
     * Report unexpected error or unknown response.
     */
    private DittoRuntimeException reportErrorOrResponse(final Command<?> command,
            @Nullable final Object response) {

        if (response instanceof Throwable throwable) {
            return reportError(command, throwable);
        } else if (response != null) {
            return reportUnknownResponse(command, response);
        } else {
            return reportError(command, new NullPointerException("Response and error were null."));
        }
    }

    /**
     * Reports an error differently based on type of the error. If the error is of type
     * {@link DittoRuntimeException}, it is returned as is
     * (without modification), otherwise it is wrapped inside a {@link DittoInternalErrorException}.
     *
     * @param throwable the error.
     * @return DittoRuntimeException suitable for transmission of the error.
     */
    private DittoRuntimeException reportError(final Command<?> command,
            @Nullable final Throwable throwable) {
        final Throwable error = throwable == null
                ? new NullPointerException("Result and error are both null")
                : throwable;
        final var dre = DittoRuntimeException.asDittoRuntimeException(
                error, t -> reportUnexpectedError(command, t));
        LOGGER.info(" - {}: {}", dre.getClass().getSimpleName(), dre.getMessage());
        return dre;
    }


    /**
     * Report unexpected error.
     */
    private DittoRuntimeException reportUnexpectedError(final Command<?> command, final Throwable error) {
        LOGGER.error("Unexpected error", error);

        return DittoInternalErrorException.newBuilder()
                .cause(error)
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    /**
     * Report unknown response.
     */
    private DittoInternalErrorException reportUnknownResponse(final Command<?> command,
            final Object response) {

        LOGGER.error("Unexpected response: <{}>", response);
        return DittoInternalErrorException.newBuilder().dittoHeaders(command.getDittoHeaders()).build();
    }

    /**
     * Report timeout.
     *
     * @param command the original command.
     * @param askTimeout the timeout exception.
     */
    private Optional<DittoRuntimeException> handleAskTimeoutForCommand(final Command<?> command,
            final Throwable askTimeout) {

        LOGGER.withCorrelationId(command.getDittoHeaders()).error("Encountered timeout in edge forwarding", askTimeout);
        return Optional.of(EdgeServiceTimeoutException.newBuilder()
                .dittoHeaders(command.getDittoHeaders())
                .build());
    }

    /**
     * ID of the actor system extension to validate the {@code AskWithRetryCommandForwarder}.
     */
    private static final class ExtensionId extends AbstractExtensionId<AskWithRetryCommandForwarder> {

        @Override
        public AskWithRetryCommandForwarder createExtension(final ExtendedActorSystem system) {

            return AkkaClassLoader.instantiate(system, AskWithRetryCommandForwarder.class,
                    AskWithRetryCommandForwarder.class.getName(),
                    List.of(ActorSystem.class),
                    List.of(system));
        }
    }

}
