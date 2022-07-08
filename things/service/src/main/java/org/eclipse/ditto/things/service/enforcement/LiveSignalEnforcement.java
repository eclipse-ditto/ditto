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
package org.eclipse.ditto.things.service.enforcement;

import static org.eclipse.ditto.policies.api.Permission.WRITE;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.UnsupportedSignalException;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.WithEntity;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.messages.model.MessageFormatInvalidException;
import org.eclipse.ditto.messages.model.MessageSendNotAllowedException;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessage;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.AbstractEnforcementReloaded;
import org.eclipse.ditto.policies.enforcement.EnforcementReloaded;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.exceptions.EventSendNotAllowedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import akka.actor.ActorSystem;

/**
 * Enforces live commands (including message commands) and live events.
 */
final class LiveSignalEnforcement
        extends AbstractEnforcementReloaded<Signal<?>, CommandResponse<?>>
        implements ThingEnforcementStrategy {

    LiveSignalEnforcement(final ActorSystem actorSystem) {
        super(actorSystem);
    }

    @Override
    public boolean isApplicable(final Signal<?> signal) {
        return Command.isLiveCommand(signal) || Event.isLiveEvent(signal) ||
                CommandResponse.isLiveCommandResponse(signal);
    }

    @Override
    public boolean responseIsApplicable(final CommandResponse<?> commandResponse) {
        return CommandResponse.isLiveCommandResponse(commandResponse);
    }

    @Override
    @SuppressWarnings("unchecked")
    public EnforcementReloaded<Signal<?>, CommandResponse<?>> getEnforcement() {
        return this;
    }

    @Override
    public CompletionStage<Signal<?>> authorizeSignal(final Signal<?> liveSignal, final PolicyEnforcer policyEnforcer) {

        final CompletionStage<Signal<?>> result;
        final var correlationIdOpt = WithDittoHeaders.getCorrelationId(liveSignal);
        if (correlationIdOpt.isPresent()) {
            final Enforcer enforcer = policyEnforcer.getEnforcer();
            if (liveSignal instanceof SendClaimMessage) {
                // claim messages require no enforcement, publish them right away:
                result = publishMessageCommand((MessageCommand<?, ?>) liveSignal, enforcer);
            } else {
                final var streamingType = StreamingType.fromSignal(liveSignal);
                if (streamingType.isPresent()) {
                    result = enforceLiveSignal(streamingType.get(), liveSignal, policyEnforcer);
                } else {
                    LOGGER.withCorrelationId(liveSignal)
                            .error("Unsupported Signal in LiveSignalEnforcement: <{}>", liveSignal);
                    throw DittoInternalErrorException.newBuilder()
                            .dittoHeaders(liveSignal.getDittoHeaders())
                            .build();
                }
            }
        } else {
            // drop live command to nonexistent things and respond with error.
            final EntityId entityId =
                    liveSignal instanceof WithEntityId withEntityId ? withEntityId.getEntityId() : EntityId.of(
                            ThingConstants.ENTITY_TYPE, "unknown:unknown");
            LOGGER.withCorrelationId(liveSignal)
                    .info("Command of type <{}> with ID <{}> could not be dispatched as no enforcer " +
                                    "could be looked up! Answering with ThingNotAccessibleException.",
                            liveSignal.getType(), entityId);
            throw ThingNotAccessibleException.newBuilder(ThingId.of(entityId))
                    .dittoHeaders(liveSignal.getDittoHeaders())
                    .build();
        }

        return result;
    }

    @Override
    public CompletionStage<Signal<?>> authorizeSignalWithMissingEnforcer(final Signal<?> signal) {
        if (signal instanceof WithEntityId withEntityId) {
            throw ThingNotAccessibleException.newBuilder(ThingId.of(withEntityId.getEntityId()))
                    .dittoHeaders(signal.getDittoHeaders())
                    .build();
        } else {
            throw ThingNotAccessibleException.newBuilder(ThingId.of("unknown:unknown"))
                    .dittoHeaders(signal.getDittoHeaders())
                    .build();
        }
    }

    @Override
    public boolean shouldFilterCommandResponse(final CommandResponse<?> commandResponse) {
        return true;
    }

    @Override
    public CompletionStage<CommandResponse<?>> filterResponse(final CommandResponse<?> commandResponse,
            final PolicyEnforcer policyEnforcer) {

        final CompletionStage<CommandResponse<?>> result;

        if (commandResponse instanceof WithEntity<?> withEntity) {
            try {
                if (withEntity.getEntity().isObject()) {
                    result = ThingCommandEnforcement.getJsonViewForCommandResponse(withEntity.getEntity().asObject(),
                                    commandResponse, policyEnforcer.getEnforcer(), enforcementDispatcher)
                            .thenApply(jsonViewForCommandResponse -> {
                                final WithEntity<?> commandResponseWithEntity =
                                        withEntity.setEntity(jsonViewForCommandResponse);
                                return (CommandResponse<?>) commandResponseWithEntity;
                            });
                } else {
                    result = CompletableFuture.completedStage(commandResponse);
                }
            } catch (final RuntimeException e) {
                throw reportError("Error after building JsonView", e, commandResponse.getDittoHeaders());
            }
        } else {
            result = CompletableFuture.completedStage(commandResponse);
        }
        return result;
    }

    private CompletionStage<Signal<?>> enforceLiveSignal(final StreamingType streamingType, final Signal<?> liveSignal,
            final PolicyEnforcer enforcer) {

        switch (streamingType) {
            case MESSAGES:
                return enforceMessageCommand((MessageCommand<?, ?>) liveSignal, enforcer.getEnforcer());
            case LIVE_EVENTS:
                return enforceLiveEvent(liveSignal, enforcer.getEnforcer());
            case LIVE_COMMANDS:
                return ThingCommandEnforcement.authorizeByPolicyOrThrow(enforcer.getEnforcer(),
                        (ThingCommand<?>) liveSignal,
                        enforcementDispatcher).thenCompose(s ->
                        addEffectedReadSubjectsToThingLiveSignal((ThingCommand<?>) liveSignal,
                                enforcer.getEnforcer()).thenApply(withReadSubjects -> {
                            LOGGER.withCorrelationId(withReadSubjects)
                                    .info("Live Command was authorized: <{}>", withReadSubjects.getType());
                            return withReadSubjects;
                        }));
            default:
                LOGGER.withCorrelationId(liveSignal)
                        .warn("Ignoring unsupported command signal: <{}>", liveSignal);
                throw UnsupportedSignalException.newBuilder(liveSignal.getType())
                        .message("The sent command is not supported as live command")
                        .dittoHeaders(liveSignal.getDittoHeaders())
                        .build();
        }
    }

    /**
     * Extend a signal by subject headers given with granted and revoked READ access.
     * The subjects are provided by the given enforcer for the resource type {@link ThingConstants#ENTITY_TYPE}.
     *
     * @param signal the signal to extend.
     * @param enforcer the enforcer.
     * @return a {@code CompletionStage} containing the extended signal.
     */
    <T extends Signal<T>> CompletionStage<T> addEffectedReadSubjectsToThingLiveSignal(final Signal<T> signal,
            final Enforcer enforcer) {

        final var resourceKey = ResourceKey.newInstance(ThingConstants.ENTITY_TYPE, signal.getResourcePath());
        return CompletableFuture.supplyAsync(() -> enforcer.getSubjectsWithPermission(resourceKey, Permission.READ),
                enforcementDispatcher).thenApply(subjects -> {

            final var newHeaders = signal.getDittoHeaders()
                    .toBuilder()
                    .readGrantedSubjects(subjects.getGranted())
                    .readRevokedSubjects(subjects.getRevoked())
                    .build();

            return signal.setDittoHeaders(newHeaders);
        });
    }

    private CompletionStage<Signal<?>> enforceLiveEvent(final Signal<?> liveSignal,
            final Enforcer enforcer) {

        return CompletableFuture.supplyAsync(() -> enforcer.hasUnrestrictedPermissions(
                        PoliciesResourceType.thingResource(liveSignal.getResourcePath()),
                        liveSignal.getDittoHeaders().getAuthorizationContext(), WRITE), enforcementDispatcher)
                .thenCompose(authorized -> {
                    if (Boolean.TRUE.equals(authorized)) {
                        LOGGER.withCorrelationId(liveSignal)
                                .info("Live Event was authorized: <{}>", liveSignal);
                        return ThingCommandEnforcement.addEffectedReadSubjectsToThingSignal((ThingEvent<?>) liveSignal,
                                enforcer, enforcementDispatcher).thenApply(s -> s);
                    } else {
                        LOGGER.withCorrelationId(liveSignal)
                                .info("Live Event was NOT authorized: <{}>", liveSignal);
                        throw EventSendNotAllowedException.newBuilder(((ThingEvent<?>) liveSignal).getEntityId())
                                .dittoHeaders(liveSignal.getDittoHeaders())
                                .build();
                    }
                });
    }

    private CompletionStage<Signal<?>> enforceMessageCommand(final MessageCommand<?, ?> command,
            final Enforcer enforcer) {

        return isAuthorized(command, enforcer).thenCompose(hasPermission -> {
            if (Boolean.TRUE.equals(hasPermission)) {
                return publishMessageCommand(command, enforcer);
            } else {
                throw rejectMessageCommand(command);
            }
        });
    }

    private CompletionStage<Signal<?>> publishMessageCommand(final MessageCommand<?, ?> command,
            final Enforcer enforcer) {

        final ResourceKey resourceKey =
                ResourceKey.newInstance(MessageCommand.RESOURCE_TYPE, command.getResourcePath());
        return CompletableFuture.supplyAsync(() -> enforcer.getSubjectsWithPermission(resourceKey, Permission.READ),
                enforcementDispatcher).thenApply(effectedSubjects -> {
            final var headersWithReadSubjects = command.getDittoHeaders()
                    .toBuilder()
                    .readGrantedSubjects(effectedSubjects.getGranted())
                    .readRevokedSubjects(effectedSubjects.getRevoked())
                    .build();
            return command.setDittoHeaders(headersWithReadSubjects);
        });
    }

    private MessageSendNotAllowedException rejectMessageCommand(final MessageCommand<?, ?> command) {
        final MessageSendNotAllowedException error =
                MessageSendNotAllowedException.newBuilder(command.getEntityId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build();

        LOGGER.withCorrelationId(command).info(
                "The command <{}> was not forwarded due to insufficient rights {}: {} - AuthorizationContext: {}",
                command.getType(), error.getClass().getSimpleName(), error.getMessage(),
                command.getDittoHeaders().getAuthorizationContext());

        return error;
    }


    private CompletionStage<Boolean> isAuthorized(final MessageCommand<?, ?> command, final Enforcer enforcer) {
        return CompletableFuture.supplyAsync(
                () -> enforcer.hasUnrestrictedPermissions(extractMessageResourceKey(command),
                        command.getDittoHeaders().getAuthorizationContext(), WRITE), enforcementDispatcher);
    }

    private static ResourceKey extractMessageResourceKey(final MessageCommand<?, ?> command) {
        try {
            return PoliciesResourceType.messageResource(command.getResourcePath());
        } catch (final IllegalArgumentException e) {
            throw MessageFormatInvalidException.newBuilder(JsonFactory.nullArray())
                    .message("Unable to determine message resource path.")
                    .description("Please verify that the thing ID, message subject and direction are set correctly.")
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
        }
    }

}
