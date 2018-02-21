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
package org.eclipse.ditto.services.gateway.proxy.actors.handlers;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.things.AclNotAllowedException;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyNotAllowedException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingConflictException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingUnavailableException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.FI;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import akka.pattern.PatternsCS;
import akka.util.Timeout;
import scala.concurrent.duration.Duration;

/**
 * This Actor handles {@link ModifyThing} commands, which means that it forwards requests made to API version 1 and
 * intersects requests to API version 2 to transform the {@link ModifyThing} to a {@link CreateThing} command and send
 * it to an adhoc-created {@link CreateThingHandlerActor} which takes care of the Policy.<br> When the Thing already did
 * exist, it forwards the {@link ModifyThing} to the passed {@code thingsActor}. This actor will terminate after it has
 * handled the request.
 * <p>
 * {@link ModifyThing} commands should be sent to this actor wrapped in a {@link ShardedMessageEnvelope} containing the
 * shard ID to forward the {@link ModifyThing} command to.
 */
public class ModifyThingHandlerActor extends AbstractActor {

    private static final Timeout ASK_TIMEOUT = new Timeout(Duration.create(10, TimeUnit.SECONDS));
    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    @Nullable
    private final ActorRef enforcerShard;

    @Nullable
    private final String enforcerId;

    @Nonnull
    private final ActorRef aclEnforcerShard;

    @Nonnull
    private final ActorRef policyEnforcerShard;

    private DittoHeaders dittoHeaders;
    private String thingId;
    private ActorRef requester;

    private ModifyThingHandlerActor(@Nullable final ActorRef enforcerShard,
            @Nullable final String enforcerId,
            @Nonnull final ActorRef aclEnforcerShard,
            @Nonnull final ActorRef policyEnforcerShard) {
        this.enforcerShard = enforcerShard;
        this.enforcerId = enforcerId;
        this.aclEnforcerShard = aclEnforcerShard;
        this.policyEnforcerShard = policyEnforcerShard;
    }

    /**
     * Creates an actor creation function for proxy actor.
     *
     * @param enforcerShard Reference to the shard region actor containing the enforcer for this command, or null if it
     * does not exist.
     * @param enforcerId ID of the enforcer actor within the shard for this command, or null if it does not exist.
     * @param aclEnforcerShard The shard region of ACL enforcer actors.
     * @param policyEnforcerShard The shard region of policy enforcer actors.
     * @return a function creating this actor from enforcer shard region and policies shard region.
     */
    public static Props props(@Nullable final ActorRef enforcerShard, @Nullable final String enforcerId,
            @Nonnull final ActorRef aclEnforcerShard, @Nonnull final ActorRef policyEnforcerShard) {

        return Props.create(ModifyThingHandlerActor.class, () ->
                new ModifyThingHandlerActor(enforcerShard, enforcerId, aclEnforcerShard, policyEnforcerShard));
    }

    @Override
    public Receive createReceive() {
        final FI.UnitApply<ModifyThing> handler = enforcerShard == null
                ? this::handleModifyThingWithoutEnforcer
                : this::handleModifyThingWithEnforcer;
        return ReceiveBuilder.create()
                .match(ModifyThing.class, modifyThing -> {
                    // handle ModifyThing command for which an enforcer is not found.
                    LogUtil.enhanceLogWithCorrelationId(log, modifyThing);

                    // set actor state for message
                    requester = getSender();
                    dittoHeaders = modifyThing.getDittoHeaders();
                    thingId = modifyThing.getThingId();

                    handler.apply(modifyThing);
                })
                .matchAny(this::handleUnknownMessage)
                .build();
    }

    private void handleUnknownMessage(final Object m) {
        log.warning("Got unknown message: {}", m);
    }

    /**
     * Handles {@link ModifyThing} modifyThing when no enforcer exists for the Thing to be modified. Transforms the
     * {@link ModifyThing modifyThing} into a {@link CreateThing} modifyThing and forwards it to a new {@link
     * CreateThingHandlerActor}. If the Thing exists, then the cache is out of date and we give up.
     *
     * @param modifyThing The {@link ModifyThing} modifyThing.
     */
    private void handleModifyThingWithoutEnforcer(final ModifyThing modifyThing) {

        if (!dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST).equals(JsonSchemaVersion.V_1) &&
                modifyThing.getThing().getAccessControlList().isPresent()) {
            // for JsonSchema version > 1, the ACL is not allowed to be present when modifying the whole Thing

            final AclNotAllowedException e = AclNotAllowedException.newBuilder(modifyThing.getId())
                    .dittoHeaders(dittoHeaders)
                    .build();
            requester.tell(ThingErrorResponse.of(modifyThing.getId(), e, dittoHeaders), getSelf());
            getContext().stop(getSelf());

        } else {
            transformModifyToCreateAndAwaitResponse(modifyThing);
        }
    }

    private void transformModifyToCreateAndAwaitResponse(final ModifyThing command) {
        // transform the ModifyThing command into a CreateThing
        final CreateThing createThing = CreateThing.of(command.getThing(), command.getInitialPolicy().orElse(null),
                command.getDittoHeaders());

        final ActorRef createThingHandlerActor = getContext().actorOf(
                CreateThingHandlerActor.props(enforcerShard, enforcerId, aclEnforcerShard, policyEnforcerShard),
                CreateThingHandlerActor.class.getSimpleName());

        // and send to CreateThingHandlerActor (which creates the Policy if needed)
        becomeCreateThingResponseAwaiting(command);
        createThingHandlerActor.tell(createThing, getSelf());
    }

    private void becomeCreateThingResponseAwaiting(final ModifyThing modifyThing) {

        final Cancellable timeout = getContext().system().scheduler().scheduleOnce(ASK_TIMEOUT.duration(), getSelf(),
                new AskTimeoutException("The ProxyActor did not respond within the specified time frame"),
                getContext().dispatcher(), null);

        getContext().become(ReceiveBuilder.create()
                .match(CreateThingResponse.class, response -> {
                    // in this case the Thing did not yet exist and is now created (together with its Policy)
                    timeout.cancel();
                    requester.tell(response, getSelf());
                    getContext().stop(getSelf());
                })
                .match(ThingConflictException.class, thingConflict -> {
                    // the thing to modify exists, but we didn't know the ID of its enforcer.
                    // it's probably a race condition, giving up.
                    timeout.cancel();

                    final Optional<Policy> policy = modifyThing.getInitialPolicy().map(PoliciesModelFactory::newPolicy);
                    if (policy.isPresent() && policy.get().iterator().hasNext()) {
                        requester.tell(PolicyNotAllowedException.newBuilder(modifyThing.getThingId())
                                .dittoHeaders(dittoHeaders)
                                .build(), getSelf());
                    } else {
                        final ThingNotAccessibleException thingNotAccessible =
                                ThingNotAccessibleException.newBuilder(modifyThing.getThingId()).build();
                        requester.tell(thingNotAccessible, getSelf());
                    }
                    getContext().stop(getSelf());
                })
                .match(DittoRuntimeException.class, cre -> {
                    timeout.cancel();
                    LogUtil.enhanceLogWithCorrelationId(log, cre.getDittoHeaders().getCorrelationId());
                    log.info("Got an unexpected DittoRuntimeException while trying to modify a Thing: {}", cre);
                    requester.tell(cre, getSelf());
                    getContext().stop(getSelf());
                })
                .match(AskTimeoutException.class, e -> {
                    log.error("Timeout exception while trying to create the Thing");
                    requester
                            .tell(ThingUnavailableException.newBuilder(thingId).dittoHeaders(dittoHeaders).build(),
                                    getSelf());
                    getContext().stop(getSelf());
                })
                .matchAny(this::handleUnknownMessage)
                .build());
    }

    /**
     * Handles {@link ModifyThing} command when an enforcer exists for the thing to be modified. If the Thing exists,
     * forward the {@link ModifyThing} command. If the Thing does not exist, transform the {@link ModifyThing} into a
     * {@link CreateThing} command and delegate to a newly created {@link CreateThingHandlerActor}.
     *
     * @param command The {@code ShardedMessageEnvelope} created from a {@code ModifyThing} command such that the ID of
     * the envelope is the ID of the enforcer (which may not be equal to the Thing ID).
     */
    private void handleModifyThingWithEnforcer(final ModifyThing command) {
        if (command.getInitialPolicy().isPresent()) {
            handleModifyThingWithInlinedPolicyWithEnforcer(command);
        } else {
            becomeModifyThingResponseAwaiting(command);
            enforcerShard.tell(createShardedMessageEnvelope(enforcerId, command), getSelf());
        }
    }

    private void handleModifyThingWithInlinedPolicyWithEnforcer(final ModifyThing command) {
        // has inlined policy with enforcer, assume thing exists.
        // thing exists; inlined policy not allowed
        requester.tell(
                PolicyNotAllowedException.newBuilder(command.getThingId()).dittoHeaders(dittoHeaders).build(),
                getSelf());
        getContext().stop(getSelf());
    }


    private void becomeModifyThingResponseAwaiting(final ModifyThing command) {

        final Cancellable timeout = getContext().system().scheduler().scheduleOnce(ASK_TIMEOUT.duration(), getSelf(),
                new AskTimeoutException("The ProxyActor did not respond within the specified time frame"),
                getContext().dispatcher(), null);

        getContext().become(ReceiveBuilder.create()
                .match(ModifyThingResponse.class, response -> {
                    // in this case the Thing did not yet exist and is now created (together with its Policy)
                    timeout.cancel();
                    requester.tell(response, getSelf());
                    getContext().stop(getSelf());
                })
                .match(ThingNotModifiableException.class, thingNotModifiable -> {
                    // either the Thing exists, or the user has no WRITE privilege on the resource thing:/
                    timeout.cancel();
                    requester.tell(thingNotModifiable, getSelf());
                    getContext().stop(getSelf());
                })
                .match(ThingNotAccessibleException.class, thingNotAccessible -> {
                    // the Thing doesn't exist, but an enforcer exists.
                    // the cache is out of date; forget the enforcer and try to create the thing.
                    timeout.cancel();
                    delegateToCreateThingHandler(command, null, null, Function.identity());
                })
                .match(DittoRuntimeException.class, cre -> {
                    timeout.cancel();
                    LogUtil.enhanceLogWithCorrelationId(log, cre.getDittoHeaders().getCorrelationId());
                    log.info("Got an unexpected DittoRuntimeException while trying to modify a Thing: {}", cre);
                    requester.tell(cre, getSelf());
                    getContext().stop(getSelf());
                })
                .match(AskTimeoutException.class, e -> {
                    log.error("Timeout exception while trying to modify the Thing");
                    requester
                            .tell(ThingUnavailableException.newBuilder(thingId).dittoHeaders(dittoHeaders).build(),
                                    getSelf());
                    getContext().stop(getSelf());
                })
                .matchAny(this::handleUnknownMessage)
                .build());

    }

    private void becomeMessageIgnoring() {
        getContext().become(ReceiveBuilder.create()
                .matchAny(message -> log.warning("Got message during ignore phase: {}", message))
                .build());
    }

    private void delegateToCreateThingHandler(final ModifyThing modifyThing,
            final ActorRef enforcerForCreateThing,
            final String enforcerShardIdForCreateThing,
            final Function<Object, Object> responseTransformer) {

        // ignore all messages, but stay alive for the child CreateThingHandlerActor
        becomeMessageIgnoring();

        // transform the ModifyThing command into a CreateThing
        final CreateThing createThing = CreateThing.of(modifyThing.getThing(),
                modifyThing.getInitialPolicy().orElse(null), modifyThing.getDittoHeaders());

        // create CreateThingHandlerActor as child
        final ActorRef createThingHandlerActor = getContext().actorOf(
                CreateThingHandlerActor.props(enforcerForCreateThing, enforcerShardIdForCreateThing, aclEnforcerShard,
                        policyEnforcerShard),
                CreateThingHandlerActor.class.getSimpleName());

        // and send to CreateThingHandlerActor (which creates the Policy if needed)
        final ActorRef self = getSelf();

        // wait until child CreateThingHandlerActor responds before killing self
        PatternsCS.ask(createThingHandlerActor, createThing, ASK_TIMEOUT)
                .thenAccept(response -> {
                    requester.tell(responseTransformer.apply(response), self);
                    asyncKillSelf(self);
                })
                .exceptionally(error -> {
                    log.error("Got error after delegating to CreateThingHandlerActor: {}", error);
                    requester.tell(responseTransformer.apply(error), self);
                    asyncKillSelf(self);
                    return null;
                });
    }

    /**
     * Kills this actor. Can also be called inside a future.
     *
     * @param self {@code ActorRef} of this actor.
     */
    private static void asyncKillSelf(final ActorRef self) {
        self.tell(PoisonPill.getInstance(), self);
    }

    private ShardedMessageEnvelope createShardedMessageEnvelope(final String enforcerId,
            final Command<?> command) {
        final DittoHeaders commandHeaders = command.getDittoHeaders();
        final JsonSchemaVersion schemaVersion =
                commandHeaders.getSchemaVersion().orElse(commandHeaders.getLatestSchemaVersion());
        return ShardedMessageEnvelope.of(enforcerId, command.getType(),
                command.toJson(schemaVersion, FieldType.regularOrSpecial()), commandHeaders);
    }
}
