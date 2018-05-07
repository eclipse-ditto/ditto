/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.proxy.actors;

import static org.eclipse.ditto.services.gateway.starter.service.util.FireAndForgetMessageUtil.getResponseForFireAndForgetMessage;
import static org.eclipse.ditto.services.gateway.streaming.StreamingType.LIVE_COMMANDS;
import static org.eclipse.ditto.services.gateway.streaming.StreamingType.LIVE_EVENTS;
import static org.eclipse.ditto.services.models.policies.Permission.READ;
import static org.eclipse.ditto.services.models.policies.Permission.WRITE;

import java.text.MessageFormat;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.messages.MessageSendNotAllowedException;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoCommand;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessage;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.EventSendNotAllowedException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingCommandToAccessExceptionRegistry;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingCommandToModifyExceptionRegistry;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;
import scala.concurrent.duration.FiniteDuration;

/**
 * Abstract implementation of {@link AbstractPolicyEnforcerActor} which provides functionality to enforce a {@link
 * Policy} for {@link org.eclipse.ditto.signals.commands.base.Command}s related to a {@link Thing}.
 */
public abstract class AbstractThingPolicyEnforcerActor extends AbstractPolicyEnforcerActor {

    private static final String THING_POLICY_DELETED_MESSAGE =
            "The Thing with ID ''{0}'' could not be accessed as its Policy with ID ''{1}'' is not or no longer " +
                    "existing.";
    private static final String THING_POLICY_DELETED_DESCRIPTION =
            "Recreate/create the Policy with ID ''{0}'' in order to get access to the Thing again.";

    private static final String SERVICE_NAME_THINGS = "Things";

    private final ActorRef thingsShardRegion;

    protected AbstractThingPolicyEnforcerActor(final ActorRef pubSubMediator, final ActorRef policiesShardRegion,
            final ActorRef thingsShardRegion, final ActorRef policyCacheFacade, final FiniteDuration cacheInterval,
            final FiniteDuration askTimeout, final Map<String, JsonFieldSelector> whitelistedJsonFields) {
        super(pubSubMediator, policiesShardRegion, policyCacheFacade, cacheInterval, askTimeout, whitelistedJsonFields);
        this.thingsShardRegion = thingsShardRegion;
    }

    protected void addThingEnforcingBehaviour(final ReceiveBuilder receiveBuilder) {
        receiveBuilder
                /* directly forward all Thing SudoCommands */
                .match(SudoCommand.class, this::forwardThingSudoCommand)

                /* Message Commands */
                .match(SendClaimMessage.class, this::publishMessageCommand)
                .match(MessageCommand.class, this::isAuthorized, this::publishMessageCommand)
                .match(MessageCommand.class, this::unauthorized)

                /* Thing Live Commands */
                .match(CreateThing.class,
                        createThing -> isLiveSignal(createThing) && isCreateThingAuthorized(createThing),
                        liveCreateThing -> publishLiveSignal(LIVE_COMMANDS.getDistributedPubSubTopic(),
                                liveCreateThing))
                .match(CreateThing.class, AbstractPolicyEnforcerActor::isLiveSignal, this::unauthorized)
                .match(ThingModifyCommand.class,
                        liveModifyThing -> isLiveSignal(liveModifyThing) &&
                                isThingModifyCommandAuthorized(liveModifyThing),
                        liveModifyThing -> publishLiveSignal(LIVE_COMMANDS.getDistributedPubSubTopic(),
                                liveModifyThing))
                .match(ThingModifyCommand.class, AbstractPolicyEnforcerActor::isLiveSignal, this::unauthorized)
                .match(ThingQueryCommand.class,
                        liveQueryThing -> isLiveSignal(liveQueryThing) && isAuthorized(liveQueryThing),
                        liveQueryThing -> publishLiveSignal(LIVE_COMMANDS.getDistributedPubSubTopic(), liveQueryThing))
                .match(ThingQueryCommand.class,
                        AbstractPolicyEnforcerActor::isLiveSignal,
                        this::unauthorized)

                /* Thing Live Events */
                .match(ThingEvent.class,
                        liveEvent -> isLiveSignal(liveEvent) && isAuthorized(liveEvent),
                        liveEvent -> publishLiveSignal(LIVE_EVENTS.getDistributedPubSubTopic(), liveEvent,
                                getRootResource()))
                .match(ThingEvent.class,
                        AbstractPolicyEnforcerActor::isLiveSignal,
                        this::unauthorized)

                /* Thing Twin Commands */
                .match(CreateThing.class, this::isCreateThingAuthorized, this::forwardThingModifyCommand)
                .match(CreateThing.class, this::unauthorized)
                .match(ThingModifyCommand.class, this::isThingModifyCommandAuthorized, this::forwardThingModifyCommand)
                .match(ThingModifyCommand.class, this::unauthorized)
                .match(ThingQueryCommand.class, this::isAuthorized, this::forwardThingQueryCommand)
                .match(ThingQueryCommand.class, this::unauthorized);
    }

    private void publishMessageCommand(final MessageCommand<?, ?> messageCommand) {

        publishLiveSignal(messageCommand.getTypePrefix(), messageCommand);

        // answer the sender immediately for fire-and-forget message commands.
        getResponseForFireAndForgetMessage(messageCommand)
                .ifPresent(response -> getSender().tell(response, getSelf()));
    }

    private void forwardThingSudoCommand(final SudoCommand command) {
        LogUtil.enhanceLogWithCorrelationId(getLogger(), command);
        logForwardingOfReceivedSignal(command, SERVICE_NAME_THINGS);
        incrementAccessCounter();
        thingsShardRegion.forward(command, getContext());
    }

    private void forwardThingModifyCommand(final ThingModifyCommand<?> command) {
        LogUtil.enhanceLogWithCorrelationId(getLogger(), command);
        final ThingCommand commandWithReadSubjects =
                enrichDittoHeaders(command, command.getResourcePath(), command.getResourceType());
        logForwardingOfReceivedSignal(commandWithReadSubjects, SERVICE_NAME_THINGS);
        incrementAccessCounter();
        thingsShardRegion.forward(commandWithReadSubjects, getContext());

        if (command.changesAuthorization()) {
            synchronizePolicy();
        }
    }

    private void forwardThingQueryCommand(final ThingQueryCommand<?> command) {
        LogUtil.enhanceLogWithCorrelationId(getLogger(), command);
        final ThingQueryCommand commandWithReadSubjects =
                enrichDittoHeaders(command, command.getResourcePath(), command.getResourceType());
        logForwardingOfReceivedSignal(commandWithReadSubjects, SERVICE_NAME_THINGS);
        incrementAccessCounter();
        thingsShardRegion.tell(commandWithReadSubjects, getSelf());

        becomeQueryingBehaviour();
        preserveQueryOriginalSender(getSender());
        scheduleQueryTimeout();
    }

    private boolean isAuthorized(final ThingQueryCommand command) {
        return isEnforcerAvailable() &&
                getPolicyEnforcer().hasPartialPermissions(PoliciesResourceType.thingResource(command.getResourcePath()),
                        command.getDittoHeaders().getAuthorizationContext(),
                        READ);
    }

    private boolean isAuthorized(final MessageCommand command) {
        return isEnforcerAvailable() &&
                getPolicyEnforcer().hasUnrestrictedPermissions(
                        PoliciesResourceType.messageResource(command.getResourcePath()),
                        command.getDittoHeaders().getAuthorizationContext(),
                        WRITE);
    }

    private boolean isAuthorized(final ThingEvent<?> event) {
        return isLiveSignal(event) &&
                isEnforcerAvailable() &&
                getPolicyEnforcer().hasUnrestrictedPermissions(
                        // only check access to root resource for now
                        PoliciesResourceType.thingResource(getRootResource()),
                        event.getDittoHeaders().getAuthorizationContext(),
                        WRITE);
    }

    private boolean isCreateThingAuthorized(final CreateThing command) {
        if (isEnforcerAvailable()) {
            return isThingModifyCommandAuthorized(command);
        } else {
            return isInlinePolicyAuthorized(command, command.getInitialPolicy(), () ->
                    isThingModifyCommandAuthorized(command));
        }
    }

    private boolean isInlinePolicyAuthorized(final ThingModifyCommand command,
            final Optional<JsonObject> inlinePolicyOpt,
            final Supplier<Boolean> fallback) {
        return inlinePolicyOpt.map(PoliciesModelFactory::newPolicy).map(inlinePolicy -> {
            rebuildPolicyEnforcer(inlinePolicy, 1L);
            return isThingModifyCommandAuthorized(command);
        }).orElseGet(fallback);
    }

    private boolean isThingModifyCommandAuthorized(final ThingModifyCommand command) {
        return isEnforcerAvailable() &&
                getPolicyEnforcer().hasUnrestrictedPermissions(
                        PoliciesResourceType.thingResource(command.getResourcePath()),
                        command.getDittoHeaders().getAuthorizationContext(),
                        WRITE);
    }

    private void unauthorized(final ThingModifyCommand command) {
        final DittoRuntimeException exception;
        // if the policy does not exist, produce a more user friendly error
        if (!isEnforcerAvailable()) {
            exception = ThingNotModifiableException.newBuilder(command.getThingId())
                    .message(MessageFormat.format(THING_POLICY_DELETED_MESSAGE, command.getThingId(), getPolicyId()))
                    .description(MessageFormat.format(THING_POLICY_DELETED_DESCRIPTION, getPolicyId()))
                    .build();
        } else {
            // if it does exist, use the "normal" message
            final ThingCommandToModifyExceptionRegistry registry = ThingCommandToModifyExceptionRegistry.getInstance();
            exception = registry.exceptionFrom(command);
        }
        logUnauthorized(command, exception);
        getSender().tell(exception, getSelf());
    }

    private void unauthorized(final ThingEvent thingEvent) {
        final DittoRuntimeException exception;
        // if the policy does not exist, produce a more user friendly error
        if (!isEnforcerAvailable()) {
            exception = ThingNotModifiableException.newBuilder(thingEvent.getThingId())
                    .message(MessageFormat.format(THING_POLICY_DELETED_MESSAGE, thingEvent.getThingId(), getPolicyId()))
                    .description(MessageFormat.format(THING_POLICY_DELETED_DESCRIPTION, getPolicyId()))
                    .build();
        } else {
            exception = EventSendNotAllowedException.newBuilder(thingEvent.getThingId()).build();
        }
        logUnauthorized(thingEvent, exception);
        getSender().tell(exception, getSelf());
    }

    private void unauthorized(final ThingQueryCommand command) {
        final DittoRuntimeException exception;
        // if the policy does not exist, produce a more user friendly error
        if (!isEnforcerAvailable()) {
            exception = ThingNotAccessibleException.newBuilder(command.getThingId())
                    .message(MessageFormat.format(THING_POLICY_DELETED_MESSAGE, command.getThingId(), getPolicyId()))
                    .description(MessageFormat.format(THING_POLICY_DELETED_DESCRIPTION, getPolicyId()))
                    .build();
        } else {
            // if it does exist, use the "normal" message
            final ThingCommandToAccessExceptionRegistry registry = ThingCommandToAccessExceptionRegistry.getInstance();
            exception = registry.exceptionFrom(command);
        }
        logUnauthorized(command, exception);
        getSender().tell(exception, getSelf());
    }

    private void unauthorized(final MessageCommand command) {
        final MessageSendNotAllowedException exception =
                MessageSendNotAllowedException.newBuilder(command.getThingId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build();
        logUnauthorized(command, exception);
        getSender().tell(exception, getSelf());
    }
}
