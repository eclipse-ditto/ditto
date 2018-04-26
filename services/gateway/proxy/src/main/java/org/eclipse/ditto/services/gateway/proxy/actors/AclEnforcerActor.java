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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.MessageSendNotAllowedException;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.models.policies.PoliciesAclMigrations;
import org.eclipse.ditto.services.models.things.ThingCacheEntry;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoCommand;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.distributedcache.actors.RegisterForCacheUpdates;
import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithThingId;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessage;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyCommandToAccessExceptionRegistry;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.EventSendNotAllowedException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingCommandToAccessExceptionRegistry;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingCommandToModifyExceptionRegistry;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAcl;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.events.things.AclEntryCreated;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;
import org.eclipse.ditto.signals.events.things.AclEntryModified;
import org.eclipse.ditto.signals.events.things.AclModified;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;

import akka.actor.AbstractActorWithStash;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.PoisonPill;
import akka.actor.Props;
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
 * Actor responsible for enforcing that the {@link AuthorizationContext} of a {@link Command} has the required {@link
 * org.eclipse.ditto.model.things.Permissions} to be processed. <ul> <li>A {@link org.eclipse.ditto.signals.commands.things.ThingCommand}
 * will be proxied to the things shard region.</li> <li>A {@link MessageCommand} will be broadcasted via distributed
 * pub-sub.</li> </ul> <p> For each {@link Thing} in {@link JsonSchemaVersion#V_1} an instance of this Actor is created
 * which caches the {@link AccessControlList} used to perform permission checks. </p>
 */
public final class AclEnforcerActor extends AbstractActorWithStash {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final String thingId;
    private final ActorRef thingsShardRegion;
    private final ActorRef policiesShardRegion;
    private final ActorRef pubSubMediator;
    private final FiniteDuration cacheInterval;
    private final FiniteDuration askTimeout;

    private final Receive enforcingBehaviour;
    private final Receive synchronizingBehaviour;

    private long accessCounter;
    private Cancellable activityCheckCancellable;
    private Cancellable synchronizationTimeout;
    private AccessControlList acl;
    private long thingRevision = -1L;
    private int stashCount = 0;
    private List<SubjectIssuer> subjectIssuersForPolicyMigration;

    private AclEnforcerActor(final ActorRef pubSubMediator,
            final ActorRef thingsShardRegion,
            final ActorRef policiesShardRegion,
            final ActorRef thingCacheFacade,
            final FiniteDuration cacheInterval,
            final FiniteDuration askTimeout,
            final List<SubjectIssuer> subjectIssuersForPolicyMigration) {

        try {
            thingId = URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException("Unsupported encoding", e);
        }
        this.thingsShardRegion = thingsShardRegion;
        this.policiesShardRegion = policiesShardRegion;
        this.pubSubMediator = pubSubMediator;
        this.cacheInterval = cacheInterval;
        this.askTimeout = askTimeout;
        this.subjectIssuersForPolicyMigration =
                Collections.unmodifiableList(new ArrayList<>(subjectIssuersForPolicyMigration));

        enforcingBehaviour = buildEnforcingBehaviour();
        synchronizingBehaviour = buildSynchronizingBehaviour();

        // subscribe for changes
        thingCacheFacade.tell(new RegisterForCacheUpdates(thingId, getSelf()), getSelf());

        synchronizeAcl();
        scheduleActivityCheck();
    }

    /**
     * Creates Akka configuration object Props for this AclEnforcerActor.
     *
     * @param pubSubMediator the Pub/Sub mediator to use for subscribing for events.
     * @param thingsShardRegion the Actor ref of the ShardRegion of {@code Things}.
     * @param policiesShardRegion the Actor ref of the ShardRegion of {@code Policies} (required to forward
     * SudoCommands).
     * @param thingCacheFacade the Actor ref to the distributed cache facade for policies.
     * @param cacheInterval the interval of how long the created AclEnforcerActor should be hold in cache w/o any
     * activity happening.
     * @param askTimeout the timeout for internal ask.
     * @param subjectIssuersForPolicyMigration subjectIssuers to be used for (policy-)subject generation from ACLs
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final ActorRef thingsShardRegion,
            final ActorRef policiesShardRegion,
            final ActorRef thingCacheFacade,
            final FiniteDuration cacheInterval,
            final FiniteDuration askTimeout,
            final List<SubjectIssuer> subjectIssuersForPolicyMigration) {

        return Props.create(AclEnforcerActor.class, new Creator<AclEnforcerActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public AclEnforcerActor create() throws Exception {
                return new AclEnforcerActor(pubSubMediator, thingsShardRegion, policiesShardRegion, thingCacheFacade,
                        cacheInterval, askTimeout, subjectIssuersForPolicyMigration);
            }
        });
    }

    @Override
    public void postStop() {
        cancelIfNonnull(activityCheckCancellable, synchronizationTimeout);
    }

    @Override
    public Receive createReceive() {
        // this behaviour should never be called, cause initial behaviour is set in constructor!
        return ReceiveBuilder.create().matchAny(m -> {
            log.warning("Received message during init phase, dropping: <{}>.", m);
            unhandled(m);
        }).build();
    }

    private Receive buildEnforcingBehaviour() {
        return ReceiveBuilder.create()
                // commands
                .match(SudoCommand.class, this::forwardSudoCommand)
                .match(org.eclipse.ditto.services.models.policies.commands.sudo.SudoCommand.class,
                        this::forwardPoliciesSudoCommand)

                .match(SendClaimMessage.class, this::forwardMessageCommand)
                .match(MessageCommand.class, this::isAuthorized, this::forwardMessageCommand)
                .match(MessageCommand.class, this::unauthorized)

                /* Thing Live Commands */
                .match(ThingModifyCommand.class,
                        liveModifyThing -> isLiveSignal(liveModifyThing) && isAuthorized(liveModifyThing),
                        liveModifyThing -> publishLiveSignal(LIVE_COMMANDS.getDistributedPubSubTopic(),
                                liveModifyThing))
                .match(ThingModifyCommand.class, AclEnforcerActor::isLiveSignal, this::unauthorized)

                .match(ThingQueryCommand.class,
                        liveQueryThing -> isLiveSignal(liveQueryThing) && isAuthorized(liveQueryThing),
                        liveQueryThing -> publishLiveSignal(LIVE_COMMANDS.getDistributedPubSubTopic(), liveQueryThing))
                .match(ThingQueryCommand.class, AclEnforcerActor::isLiveSignal, this::unauthorized)

                /* Thing Live Events */
                .match(ThingEvent.class,
                        liveEvent -> isLiveSignal(liveEvent) && isAuthorized(liveEvent),
                        liveEvent -> publishLiveSignal(LIVE_EVENTS.getDistributedPubSubTopic(), liveEvent))
                .match(ThingEvent.class, AclEnforcerActor::isLiveSignal, this::unauthorized)

                .match(CreateThing.class, createThing -> acl == null, this::forwardModifyCommand)
                .match(DeleteThing.class, this::isAuthorized, this::forwardModifyCommand)
                .match(DeleteThing.class, this::unauthorized)
                .match(ThingModifyCommand.class, this::isAuthorized, this::forwardModifyCommand)
                .match(ThingModifyCommand.class, this::unauthorized)
                .match(ThingQueryCommand.class, this::isAuthorized, this::forwardQueryCommand)
                .match(ThingQueryCommand.class, this::unauthorized)

                .match(RetrievePolicy.class, this::migrateAclIfAuthorized)

                // events
                .match(AclModified.class, this::isApplicable, event -> {
                    acl = event.getAccessControlList();
                    thingRevision = event.getRevision();
                })
                .match(AclEntryCreated.class, this::isApplicable, event -> {
                    acl = acl.merge(event.getAclEntry());
                    thingRevision = event.getRevision();
                })
                .match(AclEntryModified.class, this::isApplicable, event -> {
                    acl = acl.merge(event.getAclEntry());
                    thingRevision = event.getRevision();
                })
                .match(AclEntryDeleted.class, this::isApplicable, event -> {
                    acl = acl.removeAllPermissionsOf(event.getAuthorizationSubject());
                    thingRevision = event.getRevision();
                })
                .match(ThingCreated.class, this::isApplicable, event -> {
                    event.getThing().getAccessControlList().ifPresent(modifiedAcl -> {
                        acl = modifiedAcl;
                        thingRevision = event.getRevision();
                    });
                    thingRevision = event.getRevision();
                })
                .match(ThingModified.class, this::isApplicable, event -> {
                    event.getThing().getAccessControlList().ifPresent(modifiedAcl -> {
                        acl = modifiedAcl;
                        thingRevision = event.getRevision();
                    });
                })
                .match(ThingDeleted.class, this::isApplicable, event -> {
                    getContext().stop(getSelf());
                })
                .match(ThingEvent.class, this::unhandled)

                /* thing cache updates */
                .match(Replicator.Changed.class, this::processChangedCacheEntry)

                // any
                .match(CheckActivity.class, check -> {
                    if (check.getAccessCounter() >= accessCounter) {
                        log.debug("Stopping due to inactivity ...");
                        getContext().stop(getSelf());
                    } else {
                        scheduleActivityCheck();
                    }
                })
                .match(RetrieveAclResponse.class, resp -> log.debug(
                        "Received 'RetrieveAclResponse' in 'EnforcingBehaviour'. Ignoring that one."))
                .matchAny(m -> {
                    final String correlationId = m instanceof WithDittoHeaders ?
                            ((WithDittoHeaders) m).getDittoHeaders().getCorrelationId().orElse("") : "";
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    log.warning("Received unknown message while in 'EnforcingBehaviour': <{}>!", m);
                })
                .build();
    }

    private static boolean isLiveSignal(final Signal<?> signal) {
        return signal.getDittoHeaders().getChannel().filter(TopicPath.Channel.LIVE.getName()::equals).isPresent();
    }

    private Receive buildSynchronizingBehaviour() {
        return ReceiveBuilder.create()
                /* handle the ThingCreated event */
                .match(ThingCreated.class, tc -> acl == null, thingCreated -> {
                    LogUtil.enhanceLogWithCorrelationId(log, thingCreated);
                    cancelIfNonnull(synchronizationTimeout);
                    log.debug("Received <{}> event.", thingCreated.getType());
                    thingCreated.getThing().getAccessControlList().ifPresent(modifiedAcl -> {
                        acl = modifiedAcl;
                        thingRevision = thingCreated.getRevision();
                    });
                    getContext().become(enforcingBehaviour);
                    doUnstashAll();
                })
                .match(ThingModified.class, tm -> tm.getThing().getAccessControlList().isPresent(), thingModified -> {
                    LogUtil.enhanceLogWithCorrelationId(log, thingModified);
                    cancelIfNonnull(synchronizationTimeout);
                    log.debug("Received <{}> event.", thingModified.getType());
                    thingModified.getThing().getAccessControlList().ifPresent(modifiedAcl -> {
                        acl = modifiedAcl;
                        thingRevision = thingModified.getRevision();
                    });
                    getContext().become(enforcingBehaviour);
                    doUnstashAll();
                })
                .match(RetrieveAclResponse.class, response -> {
                    LogUtil.enhanceLogWithCorrelationId(log, response);
                    cancelIfNonnull(synchronizationTimeout);
                    acl = response.getAcl();
                    log.debug("Received <{}> response with ACL <{}>. Becoming 'EnforcingBehaviour'.",
                            response.getType(), acl.toJson());
                    getContext().become(enforcingBehaviour);
                    doUnstashAll();
                })
                .match(ThingNotAccessibleException.class, e -> {
                    LogUtil.enhanceLogWithCorrelationId(log, e);
                    cancelIfNonnull(synchronizationTimeout);
                    log.debug("Received <{}> for Thing <{}>. Becoming 'EnforcingBehaviour'.", getSimpleClassName(e),
                            thingId);
                    getContext().become(enforcingBehaviour);
                    doUnstashAll();
                })
                .match(DittoRuntimeException.class, e -> {
                    LogUtil.enhanceLogWithCorrelationId(log, e);
                    log.warning("Received unexpected <{}> exception while synchronizing ACL of Thing <{}>: <{}>! " +
                            "Sending the PoisonPill to ourselves.", e.getErrorCode(), thingId, e.getMessage());
                    stopAfterHandlingPendingMessages();
                })
                .match(AskTimeoutException.class, e -> {
                    log.warning("Synchronization of ACL of thing <{}> timed out! Sending the PoisonPill to ourselves.",
                            thingId);
                    // We don't know if the Thing is accessible or not, stopping ourselves!
                    stopAfterHandlingPendingMessages();
                })
                .match(CheckActivity.class, check -> {
                    if (check.getAccessCounter() >= accessCounter) {
                        log.debug("Stopping due to inactivity.");
                        getContext().stop(getSelf());
                    } else {
                        scheduleActivityCheck();
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
                    doStash();
                })
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

    private void forwardPoliciesSudoCommand(final org.eclipse.ditto.services.models.policies.commands.sudo.SudoCommand
            sudoCommand) {
        LogUtil.enhanceLogWithCorrelationId(log, sudoCommand.getDittoHeaders().getCorrelationId());
        log.debug("Received <{}> command. Telling Policies about it.", sudoCommand.getName());
        accessCounter++;
        policiesShardRegion.forward(sudoCommand, getContext());
    }

    private void processChangedCacheEntry(final Replicator.Changed changed) {
        final ReplicatedData replicatedData = changed.get(changed.key());
        if (replicatedData instanceof LWWRegister) {
            final LWWRegister<?> lwwRegister = (LWWRegister<?>) replicatedData;
            final Object value = lwwRegister.getValue();
            if (value instanceof ThingCacheEntry) {
                processThingCacheEntry((ThingCacheEntry) value);
            } else {
                log.warning("Received unknown cache entry <{}>!", value);
            }
        } else {
            log.warning("Received unknown cache ReplicatedData <{}>!", replicatedData);
        }
    }

    private void processThingCacheEntry(final CacheEntry cacheEntry) {
        LogUtil.enhanceLogWithCorrelationId(log, "");
        log.debug("Received new <{}> with revision <{}>.", getSimpleClassName(cacheEntry), cacheEntry.getRevision());

        if (cacheEntry.isDeleted()) {
            log.info("Cache entry was deleted: <{}>.", cacheEntry);
            acl = null;
            thingRevision = cacheEntry.getRevision();
        } else if (cacheEntry.getRevision() > thingRevision) {
            log.debug("Received cache entry has revision <{}> which is greater than the current actor's one <{}>.",
                    cacheEntry.getRevision(), thingRevision);
            synchronizeAcl();
        }
    }

    private void forwardSudoCommand(final SudoCommand<?> sudoCommand) {
        forwardCommandWithoutChangingActorState(sudoCommand);
        if (sudoCommand instanceof ThingModifyCommand && ((ThingModifyCommand) sudoCommand).changesAuthorization()) {
            synchronizeAcl();
        }
    }

    private void forwardCommandWithoutChangingActorState(final Command<?> command) {
        LogUtil.enhanceLogWithCorrelationId(log, command.getDittoHeaders().getCorrelationId());
        final Command commandWithReadSubjects = enrichDittoHeaders(command);
        log.debug("Received <{}>. Telling Things about it.", commandWithReadSubjects.getName());
        accessCounter++;
        thingsShardRegion.forward(commandWithReadSubjects, getContext());
    }

    private void forwardQueryCommand(final Command command) {
        forwardCommandWithoutChangingActorState(command);
    }

    private void forwardModifyCommand(final ThingModifyCommand command) {
        forwardCommandWithoutChangingActorState(command);
        if (command.changesAuthorization()) {
            synchronizeAcl();
        }
    }

    private void forwardMessageCommand(final MessageCommand<?, ?> command) {
        publishLiveSignal(MessageCommand.TYPE_PREFIX, command);

        // answer the sender immediately for fire-and-forget message commands.
        getResponseForFireAndForgetMessage(command)
                .ifPresent(response -> getSender().tell(response, getSelf()));
    }

    private void publishLiveSignal(final String topic, final Signal<?> signal) {
        LogUtil.enhanceLogWithCorrelationId(log, signal.getDittoHeaders().getCorrelationId());
        final Signal<?> enrichedSignal = enrichDittoHeaders(signal);
        log.debug("Received <{}>. Publishing to topic {}.", enrichedSignal.getName(), topic);
        accessCounter++;

        // using pub/sub to publish the message to any interested parties (e.g. a Websocket):
        pubSubMediator.tell(
                new DistributedPubSubMediator.Publish(topic, enrichedSignal, true),
                getSender());
    }

    private <T extends Signal> T enrichDittoHeaders(final Signal<T> signal) {
        final DittoHeaders enrichedHeaders = DittoHeaders.newBuilder(signal.getDittoHeaders())
                .readSubjects(determineReadSubjects(signal))
                .build();
        return signal.setDittoHeaders(enrichedHeaders);
    }

    private Collection<String> determineReadSubjects(final Signal<?> signal) {
        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        if (!isLiveSignal(signal)) {
            // only for non live-signals:
            if (signal instanceof CreateThing) {
                return determineReadSubjects(dittoHeaders, ((CreateThing) signal).getThing());
            } else if (signal instanceof ModifyThing &&
                    ((ModifyThing) signal).getThing().getAccessControlList().isPresent()) {
                return determineReadSubjects(dittoHeaders, ((ModifyThing) signal).getThing());
            }
        }

        if (acl != null) {
            return acl.getAuthorizedSubjectsFor(Permission.READ).stream()
                    .map(AuthorizationSubject::getId)
                    .collect(Collectors.toSet());
        } else {
            return Collections.emptySet();
        }
    }

    private static Collection<String> determineReadSubjects(final DittoHeaders dittoHeaders, final Thing thing) {
        return thing.getAccessControlList()
                .map(AclEnforcerActor::calculateReadSubjects)
                .orElseGet(() -> Collections.singleton(
                        dittoHeaders.getAuthorizationContext().getFirstAuthorizationSubject()
                                .map(AuthorizationSubject::getId).orElse("")));
    }

    private static Set<String> calculateReadSubjects(final AccessControlList acl) {
        return acl.getAuthorizedSubjectsFor(Permission.READ).stream()
                .map(AuthorizationSubject::getId)
                .collect(Collectors.toSet());
    }

    private boolean isApplicable(final WithThingId event) {
        return Objects.equals(thingId, event.getThingId());
    }

    private boolean isAuthorized(final DeleteThing command) {
        if (acl == null) {
            logNullAclForCommand(command);
            return false;
        } else {
            return acl.hasPermission(command.getDittoHeaders().getAuthorizationContext(), Permission.WRITE,
                    Permission.ADMINISTRATE);
        }
    }

    private void logNullAclForCommand(final Signal<?> signal) {
        LogUtil.enhanceLogWithCorrelationId(log, signal);
        log.info("ACL is null, therefore signal <{}> cannot be authorized.", signal.getType());
    }

    private boolean isAuthorized(final ThingModifyCommand command) {
        if (acl == null) {
            logNullAclForCommand(command);
            return false;
        } else {
            final AuthorizationContext authorizationContext = command.getDittoHeaders().getAuthorizationContext();
            return command.getEntity()
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .filter(jsonObject -> jsonObject.contains(Thing.JsonFields.ACL.getPointer()))
                    .map(jsonObject -> acl.hasPermission(authorizationContext, Permission.WRITE,
                            Permission.ADMINISTRATE))
                    .orElse(acl.hasPermission(authorizationContext, Permission.WRITE));
        }
    }

    private boolean isAuthorized(final ThingQueryCommand command) {
        if (acl == null) {
            logNullAclForCommand(command);
            return false;
        } else {
            return acl.hasPermission(command.getDittoHeaders().getAuthorizationContext(), Permission.READ);
        }
    }

    private boolean isAuthorized(final MessageCommand command) {
        if (acl == null) {
            logNullAclForCommand(command);
            return false;
        } else {
            return acl.hasPermission(command.getDittoHeaders().getAuthorizationContext(), Permission.WRITE);
        }
    }

    private boolean isAuthorized(final ThingEvent event) {
        if (acl == null) {
            logNullAclForCommand(event);
            return false;
        } else {
            return acl.hasPermission(event.getDittoHeaders().getAuthorizationContext(), Permission.WRITE);
        }
    }

    private void unauthorized(final ThingModifyCommand command) {
        final ThingCommandToModifyExceptionRegistry registry = ThingCommandToModifyExceptionRegistry.getInstance();
        final DittoRuntimeException exception = registry.exceptionFrom(command);
        logUnauthorized(command, exception);
        getSender().tell(exception, getSelf());
    }

    private void unauthorized(final ThingQueryCommand command) {
        final ThingCommandToAccessExceptionRegistry registry = ThingCommandToAccessExceptionRegistry.getInstance();
        final DittoRuntimeException exception = registry.exceptionFrom(command);
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

    private void unauthorized(final ThingEvent event) {
        final EventSendNotAllowedException exception =
                EventSendNotAllowedException.newBuilder(event.getThingId())
                        .dittoHeaders(event.getDittoHeaders())
                        .build();
        logUnauthorized(event, exception);
        getSender().tell(exception, getSelf());
    }

    private void logUnauthorized(final Signal signal, final DittoRuntimeException exception) {
        LogUtil.enhanceLogWithCorrelationId(log, signal);
        log.info("The <{}> signal was not forwarded due to insufficient rights {}: {} " +
                        "- AuthorizationSubjects: {}", signal.getType(), getSimpleClassName(exception),
                exception.getMessage(),
                signal.getDittoHeaders().getAuthorizationSubjects());
        log.debug("The AuthorizationContext for the not allowed signal '{}' was: {} - "
                        + "the ACL was: {}", signal.getType(),
                signal.getDittoHeaders().getAuthorizationContext(), acl);
    }

    private void scheduleActivityCheck() {
        log.debug("Scheduling activity check in <{}> seconds.", cacheInterval.toSeconds());
        cancelIfNonnull(activityCheckCancellable);

        activityCheckCancellable = getContext()
                .system()
                .scheduler()
                .scheduleOnce(cacheInterval, getSelf(), new CheckActivity(accessCounter), getContext().dispatcher(),
                        getSelf());
    }

    private void synchronizeAcl() {
        log.debug("Synchronizing ACL for Thing <{}>.", thingId);
        synchronizationTimeout = scheduleAskTimeout();
        getContext().become(synchronizingBehaviour);
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(JsonSchemaVersion.V_1)
                .build();
        thingsShardRegion.tell(RetrieveAcl.of(thingId, dittoHeaders), getSelf());
    }

    private Cancellable scheduleAskTimeout() {
        return getContext()
                .system()
                .scheduler()
                .scheduleOnce(askTimeout, getSelf(), new AskTimeoutException("Request timeout."),
                        getContext().dispatcher(), getSelf());
    }

    private void migrateAclIfAuthorized(final RetrievePolicy command) {
        final boolean authorized;
        if (acl == null) {
            logNullAclForCommand(command);
            authorized = false;
        } else {
            authorized = acl.hasPermission(command.getDittoHeaders().getAuthorizationContext(), Permission.READ);
        }

        if (authorized) {
            final Policy policy =
                    PoliciesAclMigrations.accessControlListToPolicyEntries(acl, command.getId(),
                            subjectIssuersForPolicyMigration);
            getSender().tell(RetrievePolicyResponse.of(thingId, policy, command.getDittoHeaders()), getSelf());
        } else {
            final PolicyCommandToAccessExceptionRegistry reg = PolicyCommandToAccessExceptionRegistry.getInstance();
            getSender().tell(reg.exceptionFrom(command), getSelf());
        }
    }

    // this method should be called only when synchronizationBehavior is active.
    private void stopAfterHandlingPendingMessages() {
        cancelIfNonnull(synchronizationTimeout, activityCheckCancellable);

        // First handle stashed messages.
        getContext().become(enforcingBehaviour);
        doUnstashAll();

        // Afterwards, send the poison pill to ourselves.
        getSelf().tell(PoisonPill.getInstance(), getSelf());
    }

    private static void cancelIfNonnull(final Cancellable... cancellables) {
        for (Cancellable cancellable : cancellables) {
            if (cancellable != null) {
                cancellable.cancel();
            }
        }
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

}
