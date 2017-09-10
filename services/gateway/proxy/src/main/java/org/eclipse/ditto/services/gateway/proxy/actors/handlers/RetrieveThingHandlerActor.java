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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import akka.util.Timeout;
import scala.concurrent.duration.Duration;

/**
 * This Actor handles {@link RetrieveThing} commands, which means that it will try to retrieve the Thing and the Policy
 * from their corresponding persistence actors and merge the results to one single Thing with Policy entries result.
 */
public class RetrieveThingHandlerActor extends AbstractActor {

    private static final int ASK_DURATION_VALUE = 20000;
    private static final Timeout ASK_TIMEOUT =
            new Timeout(Duration.create(ASK_DURATION_VALUE, TimeUnit.MILLISECONDS));

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    @Nullable
    private final ActorRef enforcerShard;

    @Nullable
    private final String enforcerId;

    private Thing thing;
    private Policy policy;

    private RetrieveThingHandlerActor(@Nullable final ActorRef enforcerShard,
            @Nullable final String enforcerId) {

        this.enforcerShard = enforcerShard;
        this.enforcerId = enforcerId;
    }

    /**
     * Creates Akka configuration object Props for this PolicyEnforcerRootActor.
     *
     * @param enforcerShard Reference to the shard region actor containing the enforcer for this command, or null if it
     * does not exist.
     * @param enforcerId ID of the enforcer actor within the shard for this command.
     * @param aclEnforcerShard The shard region of ACL enforcer actors. Not used but kept to conform to the
     * functional interface {@link ThingHandlerCreator}
     * @param policyEnforcerShard The shard region of policy enforcer actors. Not used but kept to conform to the
     * functional interface {@link ThingHandlerCreator}
     * @return the Akka configuration Props object.
     */
    @SuppressWarnings("squid:S1172") // ignore unused params (due to functional interface ThingHandlerCreator)
    public static Props props(@Nullable final ActorRef enforcerShard, @Nullable final String enforcerId,
            @Nonnull final ActorRef aclEnforcerShard, @Nonnull final ActorRef policyEnforcerShard) {

        return Props.create(RetrieveThingHandlerActor.class, () ->
                new RetrieveThingHandlerActor(enforcerShard, enforcerId));
    }

    /**
     * Function to determine, if a aggregation is needed or if the command can directly be forwarded.
     *
     * @param command the command to check.
     * @return true if an aggregation of a Thing with the corresponding Policy is needed, false otherwise.
     */
    public static boolean checkIfAggregationIsNeeded(final RetrieveThing command) {
        if (command.getImplementedSchemaVersion().equals(JsonSchemaVersion.V_1)) {
            return false;
        }

        return command.getSelectedFields().isPresent() &&
                command.getSelectedFields().get().getPointers().contains(JsonPointer.newInstance("/_policy"));
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveThing.class, this::handleRetrieveThing)
                .matchAny(m -> log.warning("Got unknown message: {}", m))
                .build();
    }

    private void handleRetrieveThing(final RetrieveThing retrieveThing) {
        LogUtil.enhanceLogWithCorrelationId(log, retrieveThing);
        log.debug("Got 'RetrieveThing': {}", retrieveThing);

        if (enforcerShard != null && enforcerId != null) {
            // start getting Thing and Policy at the same time
            awaitResponsesInParallel(retrieveThing, getSender());

            // send RetrievePolicy to the enforcer shard region with the ID of the envelope.
            // PolicyEnforcerActor forwards RetrievePolicy if authorized, and
            // AclEnforcerActor migrates ACL to policy if authorized.
            final RetrievePolicy retrievePolicy =
                    RetrievePolicy.of(enforcerId, retrieveThing.getDittoHeaders());
            final ShardedMessageEnvelope retrieveThingEnvelope = ShardedMessageEnvelope.of(enforcerId,
                    retrieveThing.getType(),
                    retrieveThing.toJson(retrieveThing.getImplementedSchemaVersion(), FieldType.regularOrSpecial()),
                    retrieveThing.getDittoHeaders());
            enforcerShard.tell(retrievePolicy, getSelf());
            enforcerShard.tell(retrieveThingEnvelope, getSelf());
        } else {
            // enforcer shard wasn't found, which means this thing does not exist.
            final ThingNotAccessibleException exception =
                    ThingNotAccessibleException.newBuilder(retrieveThing.getThingId())
                            .dittoHeaders(retrieveThing.getDittoHeaders())
                            .build();
            getSender().tell(exception, getSelf());
            getContext().stop(getSelf());
        }

    }

    private void awaitResponsesInParallel(final RetrieveThing command, final ActorRef requester) {
        final Cancellable timeout = getContext().system().scheduler().scheduleOnce(ASK_TIMEOUT.duration(), getSelf(),
                new AskTimeoutException("The policy could not be loaded within a the specified time frame"),
                getContext().dispatcher(), null);

        getContext().become(ReceiveBuilder.create()
                .match(RetrieveThingResponse.class, response -> {
                    if (policy != null) {
                        timeout.cancel();
                        thing = response.getThing();
                        sendResponse(command, requester);
                    } else {
                        thing = response.getThing();
                    }
                })
                .match(RetrievePolicyResponse.class, response -> {
                    if (thing != null) {
                        timeout.cancel();
                        sendResponse(command, requester);
                    } else {
                        policy = response.getPolicy();
                    }
                })
                .match(PolicyNotAccessibleException.class, e -> {
                    // no such policy OR no READ permission on policy:/ when queried with _policy field.
                    policy = null;
                    if (thing != null) {
                        timeout.cancel();
                        sendResponse(command, requester);
                    }
                })
                .match(ThingNotAccessibleException.class, e -> {
                    log.info("Thing was not accessible: {}", e.getMessage());
                    requester.tell(e, getSelf());
                    getContext().stop(getSelf());
                })
                .match(AskTimeoutException.class, e -> {
                    log.error("Timeout exception while trying to aggregate Thing and Policy");
                    getContext().stop(getSelf());
                })
                .match(DittoRuntimeException.class, cre -> {
                    log.warning(
                            "There occurred an unexpected DittoRuntimeException while trying to aggregate Thing and Policy: "
                                    + "{}", cre);
                    requester.tell(cre, getSelf());
                    getContext().stop(getSelf());
                })
                .matchAny(m -> log.warning("Got unknown message while waiting for responses: {}", m))
                .build());
    }

    private void sendResponse(final RetrieveThing command, final ActorRef requester) {
        final Optional<JsonFieldSelector> selectedFields = command.getSelectedFields();
        final JsonObject thingWithPolicy = JsonFactory.newObject()
                .setAll(thing.toJson(command.getImplementedSchemaVersion(), selectedFields.get()))
                .setValue("_policy", policy.toJson(command.getImplementedSchemaVersion()));

        requester.tell(RetrieveThingResponse.of(command.getThingId(), thingWithPolicy, command.getDittoHeaders()),
                getSelf());
        getContext().stop(getSelf());
    }

}
