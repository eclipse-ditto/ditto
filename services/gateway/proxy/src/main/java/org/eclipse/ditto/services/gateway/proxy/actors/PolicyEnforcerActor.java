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

import static org.eclipse.ditto.services.models.policies.Permission.READ;
import static org.eclipse.ditto.services.models.policies.Permission.WRITE;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.messages.MessageSendNotAllowedException;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyRevision;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policiesenforcers.EffectedSubjectIds;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcers;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.models.policies.PolicyCacheEntry;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoCommand;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.distributedcache.actors.RegisterForCacheUpdates;
import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithName;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.WithEntity;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessage;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandResponse;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyCommandToAccessExceptionRegistry;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyCommandToModifyExceptionRegistry;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommand;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommand;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingCommandToAccessExceptionRegistry;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingCommandToModifyExceptionRegistry;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.policies.PolicyCreated;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.policies.PolicyModified;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.actor.AbstractActorWithStash;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Status;
import akka.cluster.ddata.LWWRegister;
import akka.cluster.ddata.ReplicatedData;
import akka.cluster.ddata.Replicator;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor responsible for enforcing that {@link org.eclipse.ditto.signals.commands.base.Command}s are checked if they are
 * allowed to be processed by the responsible persistence actor.
 * <ul>
 * <li>A {@link PolicyCommand} will be proxied to the policies shard region.</li>
 * <li>A {@link org.eclipse.ditto.signals.commands.things.ThingCommand} will be proxied to the things shard region.</li>
 * <li>A {@link MessageCommand} will be proxied to {@link #MESSAGES_PROXY_ACTOR_PATH} via distributed pub-sub.</li>
 * </ul>
 * <p>
 * For each {@code policyId} an instance of this Actor is created which caches the {@code PolicyEnforcer} used to
 * determine the permissions.
 * </p>
 */
public final class PolicyEnforcerActor extends AbstractActorWithStash {

    private static final JsonPointer ROOT_RESOURCE = JsonFactory.newPointer("/");
    private static final String MESSAGES_PROXY_ACTOR_PATH = "/user/messagesRoot/messagesProxy";
    private static final String POLICY_ENFORCER_SYNC_CORRELATION_PREFIX = "policy-enforcer-sync-";

    private static final String THING_NOT_ACCESSIBLE_BECAUSEOF_POLICY_DELETED_MSG =
            "The Thing with ID ''{0}'' could not be accessed as its Policy with ID ''{1}'' is not or no longer " +
                    "existing.";

    private static final String THING_NOT_ACCESSIBLE_BECAUSEOF_POLICY_DELETED_DESC =
            "Recreate/create the Policy with ID ''{0}'' in order to get access to the Thing again.";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final String policyId;

    private final ActorRef pubSubMediator;
    private final ActorRef policiesShardRegion;
    private final ActorRef thingsShardRegion;
    private final FiniteDuration cacheInterval;
    private final FiniteDuration askTimeout;

    private final Receive enforcingBehaviour;
    private final Receive synchronizingBehaviour;
    private final Receive queryingBehaviour;

    private long accessCounter;
    private Cancellable activityCheckCancellable;
    private Cancellable synchronizationTimeout;
    private Cancellable queryTimeout;
    private ActorRef queryOriginalSender;

    private long policyRevision = -1L;
    private PolicyEnforcer policyEnforcer;
    private boolean policyLoadWasAttempted = false;
    private Set<String> cachedThingReadSubjectsOnRoot;
    private Set<String> cachedPolicyReadSubjectsOnRoot;
    private int stashCount = 0;

    private PolicyEnforcerActor(final ActorRef pubSubMediator,
            final ActorRef policiesShardRegion,
            final ActorRef thingsShardRegion,
            final ActorRef policyCacheFacade,
            final FiniteDuration cacheInterval,
            final FiniteDuration askTimeout) {

        try {
            policyId = URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException("Unsupported encoding", e);
        }
        this.pubSubMediator = pubSubMediator;
        this.policiesShardRegion = policiesShardRegion;
        this.thingsShardRegion = thingsShardRegion;
        this.cacheInterval = cacheInterval;
        this.askTimeout = askTimeout;

        enforcingBehaviour = buildEnforcingBehaviour();
        synchronizingBehaviour = buildSynchronizingBehaviour();
        queryingBehaviour = buildQueryingBehaviour();

        // subscribe for changes
        policyCacheFacade.tell(new RegisterForCacheUpdates(policyId, getSelf()), getSelf());

        synchronizePolicy();
        scheduleCheckForActivity();
    }

    /**
     * Creates Akka configuration object Props for this PolicyEnforcerActor.
     *
     * @param pubSubMediator the Pub/Sub mediator to use for subscribing for events.
     * @param policiesShardRegion the Actor ref of the ShardRegion of {@code Policies}.
     * @param thingsShardRegion the Actor ref of the ShardRegion of {@code Things}.
     * @param policyCacheFacade the Actor ref to the distributed cache facade for policies.
     * @param cacheInterval the interval of how long the created PolicyEnforcerActor should be hold in cache w/o any
     * activity happening.
     * @param askTimeout the internal timeout when retrieving the {@link Policy} or when waiting for a {@link
     * CommandResponse}.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final ActorRef policiesShardRegion,
            final ActorRef thingsShardRegion,
            final ActorRef policyCacheFacade,
            final FiniteDuration cacheInterval,
            final FiniteDuration askTimeout) {
        return Props.create(PolicyEnforcerActor.class, new Creator<PolicyEnforcerActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public PolicyEnforcerActor create() throws Exception {
                return new PolicyEnforcerActor(pubSubMediator, policiesShardRegion, thingsShardRegion,
                        policyCacheFacade, cacheInterval, askTimeout);
            }
        });
    }

    @Override
    public void postStop() {
        cancelTimeouts();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create().matchAny(m -> {
            log.warning("Got message during init phase, dropping: {}", m);
            unhandled(m);
        }).build();
    }

    private void cancelTimeouts() {
        if (activityCheckCancellable != null) {
            activityCheckCancellable.cancel();
        }
        if (synchronizationTimeout != null) {
            synchronizationTimeout.cancel();
        }
        if (queryTimeout != null) {
            queryTimeout.cancel();
        }
    }

    private Receive buildEnforcingBehaviour() {
        return ReceiveBuilder.create()
                /* directly forward all SudoCommands */
                .match(SudoCommand.class, this::forwardPolicySudoCommand)
                .match(org.eclipse.ditto.services.models.things.commands.sudo.SudoCommand.class,
                        this::forwardThingSudoCommand)

                /* Live Signals */
                .match(Signal.class, PolicyEnforcerActor::isLiveSignal, liveSignal -> {
                    final WithDittoHeaders enrichedSignal =
                            enrichDittoHeaders(liveSignal, liveSignal.getResourcePath(), getResourceType(liveSignal));
                    getSender().tell(enrichedSignal, getSelf());
                })

                /* treating PolicyCommands */
                .match(CreatePolicy.class, this::isEnforcerNull, this::forwardPolicyModifyCommand)
                .match(PolicyCommand.class, this::isEnforcerNull, command -> {
                    if (!policyLoadWasAttempted) {
                        doStash();
                        synchronizePolicy();
                    } else {
                        getSender().tell(PolicyNotAccessibleException
                                        .newBuilder(command.getId())
                                        .dittoHeaders(command.getDittoHeaders())
                                        .build(),
                                getSelf());
                    }
                })
                .match(PolicyModifyCommand.class, this::isAuthorized, this::forwardPolicyModifyCommand)
                .match(PolicyModifyCommand.class, this::unauthorized)
                .match(PolicyQueryCommand.class, this::isAuthorized, command -> {
                    tellCommand(command);
                    getContext().become(queryingBehaviour);
                    queryOriginalSender = getSender();
                    queryTimeout = scheduleAskTimeout();
                })
                .match(PolicyQueryCommand.class, this::unauthorized)

                /* treating ThingCommands */
                .match(CreateThing.class, this::isCreateThingAuthorized, this::forwardThingModifyCommand)
                .match(CreateThing.class, this::unauthorized)
                .match(ThingModifyCommand.class, this::isThingModifyCommandAuthorized, this::forwardThingModifyCommand)
                .match(ThingModifyCommand.class, this::unauthorized)
                .match(ThingQueryCommand.class, this::isAuthorized, this::tellCommand)
                .match(ThingQueryCommand.class, this::unauthorized)

                /* treating MessageCommands */
                .match(SendClaimMessage.class, this::forwardCommand)
                .match(MessageCommand.class, this::isEnforcerNull, command -> {
                    doStash();
                    synchronizePolicy();
                })
                .match(MessageCommand.class, this::isAuthorized, this::forwardCommand)
                .match(MessageCommand.class, this::unauthorized)

                /* treating PolicyEvents */
                .match(PolicyCreated.class, this::isApplicable, policyCreated -> {
                    policyEnforcer = rebuildPolicyEnforcer(policyCreated.getPolicy());
                    policyRevision = policyCreated.getRevision();
                    policyLoadWasAttempted = true;
                    publishEvent(policyCreated);
                })
                .match(PolicyModified.class, this::isApplicable, policyModified -> {
                    policyEnforcer = rebuildPolicyEnforcer(policyModified.getPolicy());
                    policyRevision = policyModified.getRevision();
                    policyLoadWasAttempted = true;
                    publishEvent(policyModified);
                })
                .match(PolicyEvent.class, this::isApplicable, event -> {
                    log.debug("Got '{}', reloading Policy now...", event.getName());
                    policyEnforcer = null;
                    policyLoadWasAttempted = false;
                    synchronizePolicy();
                    getSelf().tell(new PublishEvent(event), getSelf());
                })
                .match(PolicyEvent.class, this::unhandled)

                /* treating stashed policy events */
                .match(PublishEvent.class, publishEvent -> publishEvent((PolicyEvent) publishEvent.getEvent()))

                /* treating ThingEvents */
                .match(ThingEvent.class, this::publishEvent)

                /* policy cache updates */
                .match(Replicator.Changed.class, this::processChangedCacheEntry)

                /* treating other messages */
                .match(CheckActivity.class, check -> {
                    if (check.getAccessCounter() >= accessCounter) {
                        log.debug("Stopping due to inactivity.");
                        getContext().stop(getSelf());
                    } else {
                        scheduleCheckForActivity();
                    }
                })
                .match(RetrievePolicyResponse.class, resp -> log.debug(
                        "Received <{}> in 'EnforcingBehaviour'. Ignoring that one.", getSimpleClassName(resp)))
                .match(Status.Failure.class, failure -> {
                    Throwable cause = failure.cause();
                    if (cause instanceof JsonRuntimeException) {
                        // wrap JsonRuntimeExceptions
                        cause = new DittoJsonException((JsonRuntimeException) cause);
                    }
                    getSender().tell(cause, getSelf());
                })
                .matchAny(m -> {
                    final String correlationId = m instanceof WithDittoHeaders ?
                            ((WithDittoHeaders) m).getDittoHeaders().getCorrelationId().orElse("") : "";
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    log.warning("Received unknown message while in 'EnforcingBehaviour': <{}>!", m);
                })
                .build();
    }

    private static boolean isLiveSignal(final Signal<?> signal) {
        return "LIVE".equals(signal.getDittoHeaders().get("channel"));
    }

    private void processChangedCacheEntry(final Replicator.Changed changed) {
        final ReplicatedData replicatedData = changed.get(changed.key());
        if (replicatedData instanceof LWWRegister) {
            final LWWRegister<?> lwwRegister = (LWWRegister<?>) replicatedData;
            final Object value = lwwRegister.getValue();
            if (value instanceof PolicyCacheEntry) {
                processPolicyCacheEntry((CacheEntry) value);
            } else {
                log.warning("Received unknown cache entry <{}>.", value);
            }
        } else {
            log.warning("Received unknown cache ReplicatedData: <{}>.", replicatedData);
        }
    }

    private void processPolicyCacheEntry(final CacheEntry cacheEntry) {
        LogUtil.enhanceLogWithCorrelationId(log, "");
        log.debug("Received new <{}> with revision <{}>.", getSimpleClassName(cacheEntry), cacheEntry.getRevision());

        if (cacheEntry.isDeleted()) {
            log.info("<{}> was deleted: {}", getSimpleClassName(cacheEntry), cacheEntry);
            policyEnforcer = null;
            policyRevision = cacheEntry.getRevision();
            policyLoadWasAttempted = false;
        } else if (cacheEntry.getRevision() > policyRevision) {
            log.debug("The PolicyCacheEntry has the revision {} which is greater than the current actor's"
                    + " one <{}>.", cacheEntry.getRevision(), policyRevision);
            synchronizePolicy();
        }
    }

    private Receive buildSynchronizingBehaviour() {
        return ReceiveBuilder.create()
                /* and handle the PolicyCreated event */
                .match(PolicyCreated.class, this::isEnforcerNull, policyCreated -> {
                    LogUtil.enhanceLogWithCorrelationId(log, policyCreated);
                    synchronizationTimeout.cancel();
                    log.debug("Received <{}> event.", policyCreated.getType());
                    policyEnforcer = rebuildPolicyEnforcer(policyCreated.getPolicy());
                    policyRevision = policyCreated.getRevision();
                    policyLoadWasAttempted = true;
                    publishEvent(policyCreated);
                    getContext().become(enforcingBehaviour);
                    doUnstashAll();
                })
                /* expected response for an existing Policy after "synchronizePolicy" */
                .match(RetrievePolicyResponse.class, response -> {
                    LogUtil.enhanceLogWithCorrelationId(log, response);
                    synchronizationTimeout.cancel();
                    log.debug("Retrieved <{}> response.", response.getType());
                    policyEnforcer = rebuildPolicyEnforcer(response.getPolicy());
                    policyRevision = response.getPolicy()
                            .getRevision()
                            .map(PolicyRevision::toLong)
                            .orElse(policyRevision);
                    policyLoadWasAttempted = true;
                    getContext().become(enforcingBehaviour);
                    doUnstashAll();
                })
                /* expected response for a non existing Policy after "synchronizePolicy" */
                .match(PolicyNotAccessibleException.class, e -> {
                    LogUtil.enhanceLogWithCorrelationId(log, e);
                    synchronizationTimeout.cancel();
                    log.debug("No policy accessible for ID <{}>.", policyId);
                    policyLoadWasAttempted = true;
                    getContext().become(enforcingBehaviour);
                    doUnstashAll();
                })
                .match(AskTimeoutException.class, e -> {
                    log.warning("Synchronization of policy <{}> timed out! Sending the PoisonPill to ourselves.",
                            policyId);

                    // We don't know if the Policy is accessible or not, stopping ourselves!
                    poisonThisActor();
                })
                .match(DittoRuntimeException.class, e -> {
                    LogUtil.enhanceLogWithCorrelationId(log, e);
                    log.warning("Received unexpected <{}> exception while synchronizing policy <{}>: <{}>!" +
                            " Sending PoisonPill to ourselves.", e.getErrorCode(), policyId, e.getMessage());
                    poisonThisActor();
                })
                .match(CheckActivity.class, check -> {
                    if (check.getAccessCounter() >= accessCounter) {
                        log.debug("Stopping due to inactivity.");
                        getContext().stop(getSelf());
                    } else {
                        scheduleCheckForActivity();
                    }
                })
                .matchAny(msg -> {
                    final String correlationId = msg instanceof WithDittoHeaders ?
                            ((WithDittoHeaders) msg).getDittoHeaders().getCorrelationId().orElse("") : "";
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    if (stashCount < 1) {
                        // it's quite normal that one unexpected messages is processed when still in this behavior
                        // log to debug
                        log.debug("Received unknown message while in 'SynchronizingBehaviour': <{}>!" +
                                " Message will be stashed.", msg);
                    } else {
                        // if the stashCount is greater this is not normal - log warning!
                        log.warning("Received unknown message while in 'SynchronizingBehaviour': <{}>!" +
                                " Message will be stashed - current stashCount: <{}>", msg, stashCount);
                    }
                    doStash(); // stash all other messages
                })
                .build();
    }

    private Receive buildQueryingBehaviour() {
        return ReceiveBuilder.create()
                .match(CommandResponse.class, response -> response instanceof WithEntity, response -> {
                    queryTimeout.cancel();
                    final WithEntity withEntity = (WithEntity) response;
                    final JsonValue value = withEntity.getEntity();

                    if (value.isObject()) {
                        final String type = getResourceType(response);
                        final JsonFieldSelector whitelist = getWhitelist(response);
                        final JsonObject filterView =
                                policyEnforcer.buildJsonView(ResourceKey.newInstance(type, JsonFactory.emptyPointer()),
                                        value.asObject(), response.getDittoHeaders().getAuthorizationContext(),
                                        whitelist, Permissions.newInstance(READ));
                        try {
                            queryOriginalSender.tell(withEntity.setEntity(filterView), getSelf());
                        } catch (final DittoRuntimeException e) {
                            log.warning("Received <{}> after building JsonView with PolicyEnforcer: <{}> ",
                                    getSimpleClassName(e), e.getMessage());
                            queryOriginalSender.tell(e, getSelf());
                        }
                    } else {
                        queryOriginalSender.tell(response, getSelf());
                    }
                    getContext().become(enforcingBehaviour);
                    doUnstashAll();
                })
                .match(AskTimeoutException.class, e -> {
                    log.warning("Waiting for response timed out!");
                    getContext().become(enforcingBehaviour);
                    doUnstashAll();
                })
                .match(DittoRuntimeException.class, cre -> {
                    queryTimeout.cancel();
                    log.info("An exception occurred while waiting for a response: <{}>", cre);
                    queryOriginalSender.tell(cre, getSelf());
                    getContext().become(enforcingBehaviour);
                    doUnstashAll();
                })
                .matchAny(msg -> doStash()) // stash all other messages
                .build();
    }

    private void doStash() {
        stashCount++;
        stash();
    }

    private void doUnstashAll() {
        unstashAll();
        stashCount = 0;
    }

    // white lists of JsonViews
    private enum WhiteList implements JsonFieldSelector {

        THINGS(Thing.JsonFields.ID),

        POLICIES(Policy.JsonFields.ID);

        private final JsonFieldSelector fieldSelector; // Everything gets delegated to this object

        private WhiteList(final JsonFieldDefinition fieldDefinition, final JsonFieldDefinition... fieldDefinitions) {
            fieldSelector = JsonFactory.newFieldSelectorBuilder()
                    .addFieldDefinition(fieldDefinition, fieldDefinitions)
                    .build();
        }

        @Override
        public Set<JsonPointer> getPointers() {
            return fieldSelector.getPointers();
        }

        @Override
        public int getSize() {
            return fieldSelector.getSize();
        }

        @Override
        public boolean isEmpty() {
            return fieldSelector.isEmpty();
        }

        @Override
        public Iterator<JsonPointer> iterator() {
            return fieldSelector.iterator();
        }

    }

    /**
     * Obtains fields that should appear in a JSON view for all relevant authorization subjects. For things it contains
     * thingId, for policies it contains policyId.
     */
    private static JsonFieldSelector getWhitelist(final CommandResponse commandResponse) {
        if (commandResponse instanceof ThingCommandResponse) {
            return WhiteList.THINGS;
        } else if (commandResponse instanceof PolicyCommandResponse) {
            return WhiteList.POLICIES;
        } else {
            // the empty json field selector
            return JsonFactory.newFieldSelector(Collections.emptySet());
        }
    }

    private void forwardPolicySudoCommand(final SudoCommand command) {
        LogUtil.enhanceLogWithCorrelationId(log, command);
        logForwardingOfReceivedSignal(command, "Policies");
        accessCounter++;
        policiesShardRegion.forward(command, getContext());
        if (command instanceof PolicyModifyCommand<?>) {
            synchronizePolicy();
        }
    }

    private void logForwardingOfReceivedSignal(final WithName signal, final String targetServiceName) {
        log.debug("Received <{}> command. Telling {} about it.", signal.getName(), targetServiceName);
    }

    private void forwardPolicyCommandWithoutChangingActorState(final PolicyCommand<?> command) {
        LogUtil.enhanceLogWithCorrelationId(log, command);
        final PolicyCommand commandWithReadSubjects =
                enrichDittoHeaders(command, command.getResourcePath(), PoliciesResourceType.POLICY);
        logForwardingOfReceivedSignal(commandWithReadSubjects, "Policies");
        accessCounter++;
        policiesShardRegion.forward(commandWithReadSubjects, getContext());
    }

    private void forwardPolicyModifyCommand(final PolicyCommand command) {
        forwardPolicyCommandWithoutChangingActorState(command);
        synchronizePolicy();
    }

    private void forwardThingSudoCommand(
            final org.eclipse.ditto.services.models.things.commands.sudo.SudoCommand command) {
        LogUtil.enhanceLogWithCorrelationId(log, command);
        logForwardingOfReceivedSignal(command, "Things");
        accessCounter++;
        thingsShardRegion.forward(command, getContext());
        if (command instanceof ThingModifyCommand<?> && ((ThingModifyCommand) command).changesAuthorization()) {
            synchronizePolicy();
        }
    }

    private void forwardThingModifyCommand(final ThingModifyCommand<?> command) {
        LogUtil.enhanceLogWithCorrelationId(log, command);
        final ThingCommand commandWithReadSubjects =
                enrichDittoHeaders(command, command.getResourcePath(), PoliciesResourceType.THING);
        logForwardingOfReceivedSignal(commandWithReadSubjects, "Things");
        accessCounter++;
        thingsShardRegion.forward(commandWithReadSubjects, getContext());

        if (command.changesAuthorization()) {
            synchronizePolicy();
        }
    }

    private void forwardCommand(final MessageCommand<?, ?> command) {
        LogUtil.enhanceLogWithCorrelationId(log, command);
        final MessageCommand commandWithReadSubjects =
                enrichDittoHeaders(command, command.getResourcePath(), PoliciesResourceType.MESSAGE);
        logForwardingOfReceivedSignal(commandWithReadSubjects, "Messages");
        accessCounter++;

        // using pub/sub to publish the message to any interested parties (e.g. a Websocket):
        pubSubMediator.tell(
                new DistributedPubSubMediator.Publish(MessageCommand.TYPE_PREFIX, commandWithReadSubjects, true),
                getSender());
    }

    private void tellCommand(final PolicyCommand<?> command) {
        LogUtil.enhanceLogWithCorrelationId(log, command);
        final PolicyCommand commandWithReadSubjects =
                enrichDittoHeaders(command, command.getResourcePath(), PoliciesResourceType.POLICY);
        logForwardingOfReceivedSignal(command, "Policies");
        accessCounter++;
        policiesShardRegion.tell(commandWithReadSubjects, getSelf());
    }

    private void tellCommand(final ThingQueryCommand<?> command) {
        LogUtil.enhanceLogWithCorrelationId(log, command);
        final ThingQueryCommand commandWithReadSubjects =
                enrichDittoHeaders(command, command.getResourcePath(), PoliciesResourceType.THING);
        logForwardingOfReceivedSignal(commandWithReadSubjects, "Things");
        accessCounter++;
        thingsShardRegion.tell(commandWithReadSubjects, getSelf());
        getContext().become(queryingBehaviour);
        queryOriginalSender = getSender();
        queryTimeout = scheduleAskTimeout();
    }

    private <T extends WithDittoHeaders> T enrichDittoHeaders(final WithDittoHeaders<T> withDittoHeaders,
            final JsonPointer resourcePath, final String resourceType) {
        return withDittoHeaders.setDittoHeaders(DittoHeaders.newBuilder(withDittoHeaders.getDittoHeaders())
                .readSubjects(retrieveReadSubjects(resourcePath, resourceType))
                .build()
        );
    }

    private void publishEvent(final ThingEvent<?> event) {
        final Event enrichedEvent = enrichEvent(event);
        LogUtil.enhanceLogWithCorrelationId(log, event);
        log.debug("Publishing external enhanced ThingEvent <{}>.", enrichedEvent.getType());
        pubSubMediator.tell(new DistributedPubSubMediator.Publish(ThingEvent.TYPE_PREFIX_EXTERNAL, enrichedEvent, true),
                getSelf());
    }

    private void publishEvent(final PolicyEvent<?> event) {
        final Event enrichedEvent = enrichDittoHeaders(event, event.getResourcePath(), getResourceType(event));
        LogUtil.enhanceLogWithCorrelationId(log, event);
        log.debug("Publishing external enhanced PolicyEvent <{}>.", enrichedEvent.getType());
        pubSubMediator.tell(
                new DistributedPubSubMediator.Publish(PolicyEvent.TYPE_PREFIX_EXTERNAL, enrichedEvent, true),
                getSelf());
    }

    private Event enrichEvent(final Event<?> event) {
        return event.setDittoHeaders(DittoHeaders.newBuilder(event.getDittoHeaders())
                .readSubjects(retrieveReadSubjects(event.getResourcePath(), getResourceType(event)))
                .build());
    }

    @SuppressWarnings("squid:S1172")
    private boolean isEnforcerNull(final WithDittoHeaders withDittoHeaders) {
        return policyEnforcer == null;
    }

    private boolean isEnforcerAvailable() {
        if (policyEnforcer == null) {
            log.debug("There is no policy enforcer available for the policy enforcer actor with policy id <{}>. "
                    + "Therefore the policy check returns false.", policyId);
            return false;
        }
        return true;
    }

    private boolean isApplicable(final PolicyEvent event) {
        return event.getPolicyId().equals(policyId);
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
            policyEnforcer = rebuildPolicyEnforcer(inlinePolicy);
            policyRevision = 1L;
            policyLoadWasAttempted = true;
            return isThingModifyCommandAuthorized(command);
        }).orElseGet(fallback);
    }

    private boolean isThingModifyCommandAuthorized(final ThingModifyCommand command) {
        return isEnforcerAvailable() &&
                policyEnforcer.hasUnrestrictedPermissions(PoliciesResourceType.thingResource(command.getResourcePath()),
                        command.getDittoHeaders().getAuthorizationContext(),
                        WRITE);
    }

    private boolean isAuthorized(final ThingQueryCommand command) {
        return isEnforcerAvailable() &&
                policyEnforcer.hasPartialPermissions(PoliciesResourceType.thingResource(command.getResourcePath()),
                        command.getDittoHeaders().getAuthorizationContext(),
                        READ);
    }

    private boolean isAuthorized(final PolicyModifyCommand command) {
        return isEnforcerAvailable() &&
                policyEnforcer.hasUnrestrictedPermissions(
                        PoliciesResourceType.policyResource(command.getResourcePath()),
                        command.getDittoHeaders().getAuthorizationContext(),
                        WRITE);
    }

    private boolean isAuthorized(final PolicyQueryCommand command) {
        return isEnforcerAvailable() &&
                policyEnforcer.hasPartialPermissions(PoliciesResourceType.policyResource(command.getResourcePath()),
                        command.getDittoHeaders().getAuthorizationContext(), READ);
    }

    private boolean isAuthorized(final MessageCommand command) {
        return isEnforcerAvailable() &&
                policyEnforcer.hasUnrestrictedPermissions(
                        PoliciesResourceType.messageResource(command.getResourcePath()),
                        command.getDittoHeaders().getAuthorizationContext(),
                        WRITE);
    }

    private void unauthorized(final PolicyModifyCommand command) {
        final PolicyCommandToModifyExceptionRegistry registry = PolicyCommandToModifyExceptionRegistry.getInstance();
        final DittoRuntimeException exception = registry.exceptionFrom(command);
        logUnauthorized(command, exception);
        getSender().tell(exception, getSelf());
    }

    private void unauthorized(final PolicyQueryCommand command) {
        final PolicyCommandToAccessExceptionRegistry registry = PolicyCommandToAccessExceptionRegistry.getInstance();
        final DittoRuntimeException exception = registry.exceptionFrom(command);
        logUnauthorized(command, exception);
        getSender().tell(exception, getSelf());
    }

    private void unauthorized(final ThingModifyCommand command) {
        final DittoRuntimeException exception;
        // if the policy does not exist, produce a more user friendly error
        if (!isEnforcerAvailable()) {
            exception = ThingNotModifiableException.newBuilder(command.getThingId())
                    .message(MessageFormat.format(THING_NOT_ACCESSIBLE_BECAUSEOF_POLICY_DELETED_MSG,
                            command.getThingId(), policyId))
                    .description(MessageFormat.format(THING_NOT_ACCESSIBLE_BECAUSEOF_POLICY_DELETED_DESC, policyId))
                    .build();
        } else {
            // if it does exist, use the "normal" message
            final ThingCommandToModifyExceptionRegistry registry = ThingCommandToModifyExceptionRegistry.getInstance();
            exception = registry.exceptionFrom(command);
        }
        logUnauthorized(command, exception);
        getSender().tell(exception, getSelf());
    }

    private void unauthorized(final ThingQueryCommand command) {
        final DittoRuntimeException exception;
        // if the policy does not exist, produce a more user friendly error
        if (!isEnforcerAvailable()) {
            exception = ThingNotAccessibleException.newBuilder(command.getThingId())
                    .message(MessageFormat.format(THING_NOT_ACCESSIBLE_BECAUSEOF_POLICY_DELETED_MSG,
                            command.getThingId(), policyId))
                    .description(MessageFormat.format(THING_NOT_ACCESSIBLE_BECAUSEOF_POLICY_DELETED_DESC, policyId))
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


    private void logUnauthorized(final Command command, final DittoRuntimeException exception) {
        LogUtil.enhanceLogWithCorrelationId(log, command);
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        log.info("The command <{}> was not forwarded due to insufficient rights {}: {} - AuthorizationSubjects: {}",
                command.getType(), getSimpleClassName(exception), exception.getMessage(),
                dittoHeaders.getAuthorizationSubjects());
        log.debug("The AuthorizationContext for the not allowed command <{}> was: {} - the policyEnforcer was: {}",
                command.getType(), dittoHeaders.getAuthorizationContext(), policyEnforcer);
    }

    private static String getResourceType(final Signal<?> signal) {
        if (signal instanceof Event) {
            return getResourceType((Event<?>) signal);
        } else if (signal instanceof Command) {
            return getResourceType((Command<?>) signal);
        } else if (signal instanceof CommandResponse) {
            return getResourceType((CommandResponse<?>) signal);
        } else {
            final String msg = MessageFormat.format("Unexpected Signal <{0}>", getSimpleClassName(signal));
            throw new IllegalStateException(msg);
        }
    }

    private static String getResourceType(final Event<?> eventToEnrich) {
        if (eventToEnrich instanceof ThingEvent) {
            return PoliciesResourceType.THING;
        } else if (eventToEnrich instanceof PolicyEvent) {
            return PoliciesResourceType.POLICY;
        }

        final String msg = MessageFormat.format("Unexpected event type <{0}>", getSimpleClassName(eventToEnrich));
        throw new IllegalStateException(msg);
    }

    private static String getResourceType(final CommandResponse<?> response) {
        final String result;

        if (response instanceof ThingCommandResponse) {
            result = PoliciesResourceType.THING;
        } else if (response instanceof PolicyCommandResponse) {
            result = PoliciesResourceType.POLICY;
        } else if (response instanceof MessageCommandResponse) {
            result = PoliciesResourceType.MESSAGE;
        } else {
            final String msgTemplate = "Unexpected command response type <{0}>";
            throw new IllegalStateException(MessageFormat.format(msgTemplate, getSimpleClassName(response)));
        }

        return result;
    }

    private static String getResourceType(final Command<?> command) {
        final String result;

        if (command instanceof ThingCommand) {
            result = PoliciesResourceType.THING;
        } else if (command instanceof PolicyCommand) {
            result = PoliciesResourceType.POLICY;
        } else if (command instanceof MessageCommand) {
            result = PoliciesResourceType.MESSAGE;
        } else {
            final String msgTemplate = "Unexpected command type <{0}>";
            throw new IllegalStateException(MessageFormat.format(msgTemplate, getSimpleClassName(command)));
        }

        return result;
    }

    private Set<String> retrieveReadSubjects(final JsonPointer resourcePath, final String type) {
        if (policyEnforcer == null) {
            return new HashSet<>();
        } else if (ROOT_RESOURCE.equals(resourcePath) && type.equals(PoliciesResourceType.THING)) {
            return cachedThingReadSubjectsOnRoot;
        } else if (ROOT_RESOURCE.equals(resourcePath) && type.equals(PoliciesResourceType.POLICY)) {
            return cachedPolicyReadSubjectsOnRoot;
        } else {
            final EffectedSubjectIds effectedSubjectIds =
                    policyEnforcer.getSubjectIdsWithPermission(ResourceKey.newInstance(type, resourcePath), READ);
            return effectedSubjectIds.getGranted();
        }
    }

    private PolicyEnforcer rebuildPolicyEnforcer(final Policy policy) {
        final PolicyEnforcer newPolicyEnforcer = PolicyEnforcers.defaultEvaluator(policy);

        final EffectedSubjectIds effectedSubjectIdsOnThingResource =
                newPolicyEnforcer.getSubjectIdsWithPermission(PoliciesResourceType.thingResource(ROOT_RESOURCE), READ);
        cachedThingReadSubjectsOnRoot = effectedSubjectIdsOnThingResource.getGranted();

        final EffectedSubjectIds effectedSubjectIdsOnPoliciesResource =
                newPolicyEnforcer.getSubjectIdsWithPermission(PoliciesResourceType.policyResource(ROOT_RESOURCE), READ);
        cachedPolicyReadSubjectsOnRoot = effectedSubjectIdsOnPoliciesResource.getGranted();

        return newPolicyEnforcer;
    }

    private void synchronizePolicy() {
        final RetrievePolicy retrievePolicy = RetrievePolicy.of(policyId, DittoHeaders.newBuilder()
                .correlationId(POLICY_ENFORCER_SYNC_CORRELATION_PREFIX + policyId)
                .build());
        tellCommand(retrievePolicy);
        getContext().become(synchronizingBehaviour);
        synchronizationTimeout = scheduleAskTimeout();
    }

    private void scheduleCheckForActivity() {
        log.debug("Scheduling for Activity Check in <{}> minutes.", cacheInterval.toMinutes());
        if (activityCheckCancellable != null) {
            activityCheckCancellable.cancel();
        }
        // send a message to ourselves
        activityCheckCancellable = getContext().system().scheduler()
                .scheduleOnce(
                        cacheInterval,
                        getSelf(),
                        new CheckActivity(accessCounter), getContext().dispatcher(), getSelf());
    }

    private Cancellable scheduleAskTimeout() {
        return getContext()
                .system()
                .scheduler()
                .scheduleOnce(askTimeout, getSelf(), new AskTimeoutException("Request timeout."),
                        getContext().dispatcher(), getSelf());
    }

    private void poisonThisActor() {
        cancelTimeouts();

        // First handle stashed messages.
        getContext().become(enforcingBehaviour);
        doUnstashAll();

        // Afterwards, send the poison pill to ourselves.
        getSelf().tell(PoisonPill.getInstance(), getSelf());
    }

    private static String getSimpleClassName(final Object o) {
        return o.getClass().getSimpleName();
    }

    /**
     * Message sent to self to check for activity.
     */
    @Immutable
    private static final class CheckActivity {

        private final long accessCounter;

        CheckActivity(final long accessCounter) {
            this.accessCounter = accessCounter;
        }

        long getAccessCounter() {
            return accessCounter;
        }

    }

    private static final class PublishEvent {

        private final Event event;

        PublishEvent(final Event event) {
            this.event = event;
        }

        Event getEvent() {
            return event;
        }

    }

}
