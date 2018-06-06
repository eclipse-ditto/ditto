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
package org.eclipse.ditto.services.policies.persistence.actors.policy;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyBuilder;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyLifecycle;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.policies.persistence.actors.AbstractReceiveStrategy;
import org.eclipse.ditto.services.policies.persistence.actors.ReceiveStrategy;
import org.eclipse.ditto.services.policies.persistence.actors.StrategyAwareReceiveBuilder;
import org.eclipse.ditto.services.policies.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.signals.base.WithId;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyConflictException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyEntryModificationInvalidException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyEntryNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyModificationInvalidException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.ResourceNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.SubjectNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyEntry;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteResource;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteResourceResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteSubject;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteSubjectResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntries;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntriesResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntry;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResource;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResourceResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResources;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResourcesResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubject;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjectResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjects;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjectsResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntries;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntriesResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntry;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResource;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResourceResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResources;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResourcesResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubject;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjectResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjects;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjectsResponse;
import org.eclipse.ditto.signals.events.policies.PolicyCreated;
import org.eclipse.ditto.signals.events.policies.PolicyDeleted;
import org.eclipse.ditto.signals.events.policies.PolicyEntriesModified;
import org.eclipse.ditto.signals.events.policies.PolicyEntryCreated;
import org.eclipse.ditto.signals.events.policies.PolicyEntryDeleted;
import org.eclipse.ditto.signals.events.policies.PolicyEntryModified;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.policies.PolicyModified;
import org.eclipse.ditto.signals.events.policies.ResourceCreated;
import org.eclipse.ditto.signals.events.policies.ResourceDeleted;
import org.eclipse.ditto.signals.events.policies.ResourceModified;
import org.eclipse.ditto.signals.events.policies.ResourcesModified;
import org.eclipse.ditto.signals.events.policies.SubjectCreated;
import org.eclipse.ditto.signals.events.policies.SubjectDeleted;
import org.eclipse.ditto.signals.events.policies.SubjectModified;
import org.eclipse.ditto.signals.events.policies.SubjectsModified;

import com.typesafe.config.Config;

import akka.ConfigurationException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.cluster.sharding.ClusterSharding;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.function.Procedure;
import akka.japi.pf.FI;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.DeleteMessagesFailure;
import akka.persistence.DeleteMessagesSuccess;
import akka.persistence.DeleteSnapshotFailure;
import akka.persistence.DeleteSnapshotSuccess;
import akka.persistence.RecoveryCompleted;
import akka.persistence.SaveSnapshotFailure;
import akka.persistence.SaveSnapshotSuccess;
import akka.persistence.SnapshotMetadata;
import akka.persistence.SnapshotOffer;
import scala.concurrent.duration.Duration;

/**
 * PersistentActor which "knows" the state of a single {@link Policy}.
 */
public final class PolicyPersistenceActor extends AbstractPersistentActor {

    /**
     * The prefix of the persistenceId for Policies.
     */
    public static final String PERSISTENCE_ID_PREFIX = "policy:";

    /**
     * The ID of the journal plugin this persistence actor uses.
     */
    private static final String JOURNAL_PLUGIN_ID = "akka-contrib-mongodb-persistence-policies-journal";

    /**
     * The ID of the snapshot plugin this persistence actor uses.
     */
    private static final String SNAPSHOT_PLUGIN_ID = "akka-contrib-mongodb-persistence-policies-snapshots";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final String policyId;
    private final SnapshotAdapter<Policy> snapshotAdapter;
    private final ActorRef pubSubMediator;
    private final java.time.Duration activityCheckInterval;
    private final java.time.Duration activityCheckDeletedInterval;
    private final java.time.Duration snapshotInterval;
    private final long snapshotThreshold;
    private final Receive handlePolicyEvents;
    private final boolean snapshotDeleteOld;
    private final boolean eventsDeleteOld;
    private Policy policy;
    private long accessCounter;
    private long lastSnapshotSequenceNr = -1;
    private Cancellable activityChecker;
    private Cancellable snapshotter;
    private Runnable invokeAfterSnapshotRunnable;
    private boolean snapshotInProgress;

    private PolicyPersistenceActor(final String policyId,
            final SnapshotAdapter<Policy> snapshotAdapter,
            final ActorRef pubSubMediator) {

        this.policyId = policyId;
        this.pubSubMediator = pubSubMediator;
        this.snapshotAdapter = snapshotAdapter;

        final Config config = getContext().system().settings().config();
        activityCheckInterval = config.getDuration(ConfigKeys.Policy.ACTIVITY_CHECK_INTERVAL);
        activityCheckDeletedInterval = config.getDuration(ConfigKeys.Policy.ACTIVITY_CHECK_DELETED_INTERVAL);
        snapshotInterval = config.getDuration(ConfigKeys.Policy.SNAPSHOT_INTERVAL);
        snapshotThreshold = config.getLong(ConfigKeys.Policy.SNAPSHOT_THRESHOLD);
        snapshotDeleteOld = config.getBoolean(ConfigKeys.Policy.SNAPSHOT_DELETE_OLD);
        eventsDeleteOld = config.getBoolean(ConfigKeys.Policy.EVENTS_DELETE_OLD);

        if (snapshotThreshold < 0) {
            throw new ConfigurationException(String.format("Config setting '%s' must be positive, but is: %d.",
                    ConfigKeys.Policy.SNAPSHOT_THRESHOLD, snapshotThreshold));
        }

        handlePolicyEvents = ReceiveBuilder.create()

                // # Policy Creation Recovery
                .match(PolicyCreated.class, pc -> policy = pc.getPolicy().toBuilder()
                        .setLifecycle(PolicyLifecycle.ACTIVE)
                        .setRevision(lastSequenceNr())
                        .setModified(pc.getTimestamp().orElse(null))
                        .build())

                // # Policy Modification Recovery
                .match(PolicyModified.class, pm -> {
                    // we need to use the current policy as base otherwise we would loose its state
                    final PolicyBuilder copyBuilder = policy.toBuilder();
                    copyBuilder.removeAll(policy); // remove all old policyEntries!
                    copyBuilder.setAll(pm.getPolicy().getEntriesSet()); // add the new ones
                    policy = copyBuilder.setRevision(lastSequenceNr())
                            .setModified(pm.getTimestamp().orElse(null))
                            .build();
                })

                // # Policy Deletion Recovery
                .match(PolicyDeleted.class, pd -> {
                    if (policy != null) {
                        policy = policy.toBuilder()
                                .setLifecycle(PolicyLifecycle.DELETED)
                                .setRevision(lastSequenceNr())
                                .setModified(pd.getTimestamp().orElse(null))
                                .build();
                    } else {
                        log.warning("Policy was null when 'PolicyDeleted' event should have been applied on recovery.");
                    }
                })

                // # Policy Entries Modification Recovery
                .match(PolicyEntriesModified.class, pem -> policy = policy.toBuilder()
                        .removeAll(policy.getEntriesSet())
                        .setAll(pem.getPolicyEntries())
                        .setRevision(lastSequenceNr())
                        .setModified(pem.getTimestamp().orElse(null))
                        .build())


                // # Policy Entry Creation Recovery
                .match(PolicyEntryCreated.class, pec -> policy = policy.toBuilder()
                        .set(pec.getPolicyEntry())
                        .setRevision(lastSequenceNr())
                        .setModified(pec.getTimestamp().orElse(null))
                        .build())

                // # Policy Entry Modification Recovery
                .match(PolicyEntryModified.class, pem -> policy = policy.toBuilder()
                        .set(pem.getPolicyEntry())
                        .setRevision(lastSequenceNr())
                        .setModified(pem.getTimestamp().orElse(null))
                        .build())

                // # Policy Entry Deletion Recovery
                .match(PolicyEntryDeleted.class, ped -> policy = policy.toBuilder()
                        .remove(ped.getLabel())
                        .setRevision(lastSequenceNr())
                        .setModified(ped.getTimestamp().orElse(null))
                        .build())

                // # Subjects Modification Recovery
                .match(SubjectsModified.class, sm -> policy.getEntryFor(sm.getLabel())
                        .map(policyEntry -> PoliciesModelFactory
                                .newPolicyEntry(sm.getLabel(), sm.getSubjects(), policyEntry.getResources()))
                        .ifPresent(modifiedPolicyEntry -> policy = policy.toBuilder()
                                .set(modifiedPolicyEntry)
                                .setRevision(lastSequenceNr())
                                .setModified(sm.getTimestamp().orElse(null))
                                .build()))

                // # Subject Creation Recovery
                .match(SubjectCreated.class, sc -> policy.getEntryFor(sc.getLabel())
                        .map(policyEntry -> PoliciesModelFactory
                                .newPolicyEntry(sc.getLabel(), policyEntry.getSubjects().setSubject(sc.getSubject()),
                                        policyEntry.getResources()))
                        .ifPresent(modifiedPolicyEntry -> policy = policy.toBuilder()
                                .set(modifiedPolicyEntry)
                                .setRevision(lastSequenceNr())
                                .setModified(sc.getTimestamp().orElse(null))
                                .build()))

                // # Subject Modification Recovery
                .match(SubjectModified.class, sm -> policy.getEntryFor(sm.getLabel())
                        .map(policyEntry -> PoliciesModelFactory
                                .newPolicyEntry(sm.getLabel(), policyEntry.getSubjects().setSubject(sm.getSubject()),
                                        policyEntry.getResources()))
                        .ifPresent(modifiedPolicyEntry -> policy = policy.toBuilder()
                                .set(modifiedPolicyEntry)
                                .setRevision(lastSequenceNr())
                                .setModified(sm.getTimestamp().orElse(null))
                                .build()))

                // # Subject Deletion Recovery
                .match(SubjectDeleted.class, sd -> policy = policy.toBuilder()
                        .forLabel(sd.getLabel())
                        .removeSubject(sd.getSubjectId())
                        .setRevision(lastSequenceNr())
                        .setModified(sd.getTimestamp().orElse(null))
                        .build())

                // # Resources Modification Recovery
                .match(ResourcesModified.class, rm -> policy.getEntryFor(rm.getLabel())
                        .map(policyEntry -> PoliciesModelFactory
                                .newPolicyEntry(rm.getLabel(), policyEntry.getSubjects(), rm.getResources()))
                        .ifPresent(modifiedPolicyEntry -> policy = policy.toBuilder()
                                .set(modifiedPolicyEntry)
                                .setRevision(lastSequenceNr())
                                .setModified(rm.getTimestamp().orElse(null))
                                .build()))

                // # Resource Creation Recovery
                .match(ResourceCreated.class, rc -> policy.getEntryFor(rc.getLabel())
                        .map(policyEntry -> PoliciesModelFactory.newPolicyEntry(rc.getLabel(),
                                policyEntry.getSubjects(),
                                policyEntry.getResources().setResource(rc.getResource())))
                        .ifPresent(modifiedPolicyEntry -> policy = policy.toBuilder()
                                .set(modifiedPolicyEntry)
                                .setRevision(lastSequenceNr())
                                .setModified(rc.getTimestamp().orElse(null))
                                .build()))

                // # Resource Modification Recovery
                .match(ResourceModified.class, rm -> policy.getEntryFor(rm.getLabel())
                        .map(policyEntry -> PoliciesModelFactory.newPolicyEntry(rm.getLabel(),
                                policyEntry.getSubjects(),
                                policyEntry.getResources().setResource(rm.getResource())))
                        .ifPresent(modifiedPolicyEntry -> policy = policy.toBuilder()
                                .set(modifiedPolicyEntry)
                                .setRevision(lastSequenceNr())
                                .setModified(rm.getTimestamp().orElse(null))
                                .build()))

                // # Resource Deletion Recovery
                .match(ResourceDeleted.class, rd -> policy = policy.toBuilder()
                        .forLabel(rd.getLabel())
                        .removeResource(rd.getResourceKey())
                        .setRevision(lastSequenceNr())
                        .setModified(rd.getTimestamp().orElse(null))
                        .build())

                .build();
    }

    /**
     * Creates Akka configuration object {@link Props} for this PolicyPersistenceActor.
     *
     * @param policyId the ID of the Policy this Actor manages.
     * @param snapshotAdapter the adapter to serialize Policy snapshots.
     * @param pubSubMediator the PubSub mediator actor.
     * @return the Akka configuration Props object
     */
    public static Props props(final String policyId,
            final SnapshotAdapter<Policy> snapshotAdapter,
            final ActorRef pubSubMediator) {
        return Props.create(PolicyPersistenceActor.class, new Creator<PolicyPersistenceActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public PolicyPersistenceActor create() {
                return new PolicyPersistenceActor(policyId, snapshotAdapter, pubSubMediator);
            }
        });
    }

    /**
     * Retrieves the ShardRegion of "Policy". PolicyCommands can be sent to this region which handles dispatching them
     * in the cluster (onto the cluster node containing the shard).
     *
     * @param system the ActorSystem in which to lookup the ShardRegion.
     * @return the ActorRef to the ShardRegion.
     */
    public static ActorRef getShardRegion(final ActorSystem system) {
        return ClusterSharding.get(system).shardRegion(PoliciesMessagingConstants.SHARD_REGION);
    }

    private static Instant getEventTimestamp() {
        return Instant.now();
    }

    private void scheduleCheckForPolicyActivity(final long intervalInSeconds) {
        if (activityChecker != null) {
            activityChecker.cancel();
        }
        // send a message to ourselves:
        activityChecker = getContext().system().scheduler()
                .scheduleOnce(Duration.apply(intervalInSeconds, TimeUnit.SECONDS), getSelf(),
                        new CheckForActivity(lastSequenceNr(), accessCounter), getContext().dispatcher(), null);
    }

    private void scheduleSnapshot(final long intervalInSeconds) {
        // send a message to ourselft:
        snapshotter = getContext().system().scheduler()
                .scheduleOnce(Duration.apply(intervalInSeconds, TimeUnit.SECONDS), getSelf(),
                        TakeSnapshotInternal.INSTANCE,
                        getContext().dispatcher(), null);
    }

    @Override
    public String persistenceId() {
        return PERSISTENCE_ID_PREFIX + policyId;
    }

    @Override
    public String journalPluginId() {
        return JOURNAL_PLUGIN_ID;
    }

    @Override
    public String snapshotPluginId() {
        return SNAPSHOT_PLUGIN_ID;
    }

    @Override
    public void postStop() {
        super.postStop();
        invokeAfterSnapshotRunnable = null;
        if (activityChecker != null) {
            activityChecker.cancel();
        }
        if (snapshotter != null) {
            snapshotter.cancel();
        }
    }

    @Override
    public Receive createReceive() {
        /*
         * First no Policy for the ID exists at all. Thus the only command this Actor reacts to is CreatePolicy.
         * This behaviour changes as soon as a Policy was created.
         */
        final StrategyAwareReceiveBuilder initialReceiveCommandBuilder = new StrategyAwareReceiveBuilder();
        initialReceiveCommandBuilder.match(new CreatePolicyStrategy());
        initialReceiveCommandBuilder.match(new CheckForActivityStrategy());
        initialReceiveCommandBuilder.matchAny(new MatchAnyDuringInitializeStrategy());
        return initialReceiveCommandBuilder.build();
    }

    @Override
    public Receive createReceiveRecover() {
        // defines how state is updated during recovery
        return handlePolicyEvents.orElse(ReceiveBuilder.create()

                // # Snapshot handling
                .match(SnapshotOffer.class, ss -> {
                    policy = snapshotAdapter.fromSnapshotStore(ss);
                    lastSnapshotSequenceNr = ss.metadata().sequenceNr();
                })

                // # Recovery handling
                .match(RecoveryCompleted.class, rc -> {
                    if (policy != null) {
                        log.debug("Policy <{}> was recovered.", policyId);

                        if (isPolicyActive()) {
                            becomePolicyCreatedHandler();
                        } else if (isPolicyDeleted()) {
                            becomePolicyDeletedHandler();
                        } else {
                            log.error("Unknown lifecycle state <{}> for Policy <{}>.", policy.getLifecycle(), policyId);
                        }

                    }
                })

                // # Handle unknown
                .matchAny(m -> log.warning("Unknown recover message: {}", m))
                .build());
    }

    /*
     * Now as the {@code policy} reference is not {@code null} the strategies which act on this reference can
     * be activated. In return the strategy for the CreatePolicy command is not needed anymore.
     */
    private void becomePolicyCreatedHandler() {
        final Collection<ReceiveStrategy<?>> policyCreatedStrategies = initPolicyCreatedStrategies();
        final StrategyAwareReceiveBuilder strategyAwareReceiveBuilder = new StrategyAwareReceiveBuilder();
        policyCreatedStrategies.forEach(strategyAwareReceiveBuilder::match);
        strategyAwareReceiveBuilder.matchAny(new MatchAnyAfterInitializeStrategy());

        getContext().become(strategyAwareReceiveBuilder.build(), true);
        getContext().getParent().tell(new PolicySupervisorActor.ManualReset(), getSelf());

        scheduleCheckForPolicyActivity(activityCheckInterval.getSeconds());
        scheduleSnapshot(snapshotInterval.getSeconds());
    }

    private Collection<ReceiveStrategy<?>> initPolicyCreatedStrategies() {
        final Collection<ReceiveStrategy<?>> result = new ArrayList<>();

        // Policy level
        result.add(new PolicyConflictStrategy());
        result.add(new ModifyPolicyStrategy());
        result.add(new RetrievePolicyStrategy());
        result.add(new DeletePolicyStrategy());

        // Policy Entries
        result.add(new ModifyPolicyEntriesStrategy());
        result.add(new RetrievePolicyEntriesStrategy());

        // Policy Entry
        result.add(new ModifyPolicyEntryStrategy());
        result.add(new RetrievePolicyEntryStrategy());
        result.add(new DeletePolicyEntryStrategy());

        // Subjects
        result.add(new ModifySubjectsStrategy());
        result.add(new ModifySubjectStrategy());
        result.add(new RetrieveSubjectsStrategy());
        result.add(new RetrieveSubjectStrategy());
        result.add(new DeleteSubjectStrategy());

        // Resources
        result.add(new ModifyResourcesStrategy());
        result.add(new ModifyResourceStrategy());
        result.add(new RetrieveResourcesStrategy());
        result.add(new RetrieveResourceStrategy());
        result.add(new DeleteResourceStrategy());

        // Sudo
        result.add(new SudoRetrievePolicyStrategy());

        // Persistence specific
        result.add(new SaveSnapshotSuccessStrategy());
        result.add(new SaveSnapshotFailureStrategy());
        result.add(new DeleteSnapshotSuccessStrategy());
        result.add(new DeleteSnapshotFailureStrategy());
        result.add(new DeleteMessagesSuccessStrategy());
        result.add(new DeleteMessagesFailureStrategy());
        result.add(new CheckForActivityStrategy());
        result.add(new TakeSnapshotInternalStrategy());

        return result;
    }

    private void becomePolicyDeletedHandler() {
        final Collection<ReceiveStrategy<?>> policyDeletedStrategies = initPolicyDeletedStrategies();
        final StrategyAwareReceiveBuilder strategyAwareReceiveBuilder = new StrategyAwareReceiveBuilder();
        policyDeletedStrategies.forEach(strategyAwareReceiveBuilder::match);
        strategyAwareReceiveBuilder.matchAny(new PolicyNotFoundStrategy());

        getContext().become(strategyAwareReceiveBuilder.build(), true);
        getContext().getParent().tell(new PolicySupervisorActor.ManualReset(), getSelf());

        if (activityChecker != null) {
            activityChecker.cancel();
        }
        if (snapshotter != null) {
            snapshotter.cancel();
        }
        /*
         * Check in the next X minutes and therefore
         * - stay in-memory for a short amount of minutes after deletion
         * - get a Snapshot when removed from memory
         */
        scheduleCheckForPolicyActivity(activityCheckDeletedInterval.getSeconds());
    }

    private Collection<ReceiveStrategy<?>> initPolicyDeletedStrategies() {
        final Collection<ReceiveStrategy<?>> result = new ArrayList<>();
        result.add(new CreatePolicyStrategy());

        // Persistence specific
        result.add(new SaveSnapshotSuccessStrategy());
        result.add(new SaveSnapshotFailureStrategy());
        result.add(new DeleteSnapshotSuccessStrategy());
        result.add(new DeleteSnapshotFailureStrategy());
        result.add(new DeleteMessagesSuccessStrategy());
        result.add(new DeleteMessagesFailureStrategy());
        result.add(new CheckForActivityStrategy());

        return result;
    }

    private <E extends PolicyEvent> void processEvent(final E event, final Procedure<E> handler) {
        log.debug("About to persist Event <{}>.", event.getType());

        persist(event, persistedEvent -> {
            log.info("Successfully persisted Event <{}>.", event.getType());

            // after the event was persisted, apply the event on the current actor state
            handlePolicyEvents.onMessage().apply(persistedEvent);

            /*
             * The event has to be applied before creating the snapshot, otherwise a snapshot with new
             * sequence no (e.g. 2), but old thing revision no (e.g. 1) will be created. This can lead to serious
             * aftereffects.
             */
            handler.apply(persistedEvent);

            // save a snapshot if there were too many changes since the last snapshot
            if ((lastSequenceNr() - lastSnapshotSequenceNr) > snapshotThreshold) {
                doSaveSnapshot(null);
            }
            notifySubscribers(event);
        });
    }

    private long getNextRevision() {
        return lastSequenceNr() + 1;
    }

    private boolean isPolicyActive() {
        return policy != null && policy.hasLifecycle(PolicyLifecycle.ACTIVE);
    }

    private boolean isPolicyDeleted() {
        return null == policy || policy.hasLifecycle(PolicyLifecycle.DELETED);
    }

    private void doSaveSnapshot(final Runnable invokeAfterSnapshotRunnable) {
        if (snapshotInProgress) {
            log.debug("Already requested taking a Snapshot - not doing it again.");
        } else {
            snapshotInProgress = true;
            this.invokeAfterSnapshotRunnable = invokeAfterSnapshotRunnable;
            log.debug("Attempting to save Snapshot for <{}> ...", policy);
            // save a snapshot
            final Object snapshotToStore = snapshotAdapter.toSnapshotStore(policy);
            saveSnapshot(snapshotToStore);
        }
    }

    private void notifySubscribers(final PolicyEvent event) {
        pubSubMediator.tell(new DistributedPubSubMediator.Publish(PolicyEvent.TYPE_PREFIX, event, true), getSelf());
    }

    private void policyEntryNotFound(final Label label, final DittoHeaders dittoHeaders) {
        notifySender(PolicyEntryNotAccessibleException.newBuilder(policyId, label).dittoHeaders(dittoHeaders).build());
    }

    private void subjectNotFound(final Label label, final CharSequence subjectId, final DittoHeaders dittoHeaders) {
        notifySender(SubjectNotAccessibleException.newBuilder(policyId, label.toString(), subjectId)
                .dittoHeaders(dittoHeaders)
                .build());
    }

    private void resourceNotFound(final Label label, final ResourceKey resourceKey, final DittoHeaders dittoHeaders) {
        notifySender(ResourceNotAccessibleException.newBuilder(policyId, label, resourceKey.toString())
                .dittoHeaders(dittoHeaders)
                .build());
    }

    private WithDittoHeaders policyNotFound(final DittoHeaders dittoHeaders) {
        return PolicyNotAccessibleException.newBuilder(policyId).dittoHeaders(dittoHeaders).build();
    }

    private void policyInvalid(final String message, final DittoHeaders dittoHeaders) {
        final PolicyModificationInvalidException exception = PolicyModificationInvalidException.newBuilder(policyId)
                .description(message)
                .dittoHeaders(dittoHeaders)
                .build();

        notifySender(exception);
    }

    private void policyEntryInvalid(final Label label, final String message, final DittoHeaders dittoHeaders) {
        final PolicyEntryModificationInvalidException exception =
                PolicyEntryModificationInvalidException.newBuilder(policyId, label)
                        .description(message)
                        .dittoHeaders(dittoHeaders)
                        .build();

        notifySender(exception);
    }

    private void notifySender(final WithDittoHeaders message) {
        accessCounter++;
        notifySender(getSender(), message);
    }

    private void notifySender(final ActorRef sender, final WithDittoHeaders message) {
        accessCounter++;
        sender.tell(message, getSelf());
    }

    /**
     * Message the PolicyPersistenceActor can send to itself to check for activity of the Actor and terminate itself
     * if there was no activity since the last check.
     */
    private static final class CheckForActivity {

        private final long currentSequenceNr;
        private final long currentAccessCounter;

        /**
         * Constructs a new {@code CheckForActivity} message containing the current "lastSequenceNo" of the
         * PolicyPersistenceActor.
         *
         * @param currentSequenceNr the current {@code PoliciesModelFactory.lastSequenceNr()} of the
         * PolicyPersistenceActor.
         * @param currentAccessCounter the current {@code accessCounter} of the PolicyPersistenceActor.
         */
        CheckForActivity(final long currentSequenceNr, final long currentAccessCounter) {
            this.currentSequenceNr = currentSequenceNr;
            this.currentAccessCounter = currentAccessCounter;
        }

        /**
         * Returns the current {@code PoliciesModelFactory.lastSequenceNr()} of the PolicyPersistenceActor.
         *
         * @return the current {@code PoliciesModelFactory.lastSequenceNr()} of the PolicyPersistenceActor.
         */
        long getCurrentSequenceNr() {
            return currentSequenceNr;
        }

        /**
         * Returns the current {@code accessCounter} of the PolicyPersistenceActor.
         *
         * @return the current {@code accessCounter} of the PolicyPersistenceActor.
         */
        long getCurrentAccessCounter() {
            return currentAccessCounter;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final CheckForActivity that = (CheckForActivity) o;
            return Objects.equals(currentSequenceNr, that.currentSequenceNr) &&
                    Objects.equals(currentAccessCounter, that.currentAccessCounter);
        }

        @Override
        public int hashCode() {
            return Objects.hash(currentSequenceNr, currentAccessCounter);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" + "currentSequenceNr=" + currentSequenceNr +
                    ", currentAccessCounter=" + currentAccessCounter + "]";
        }

    }

    /**
     * Message the PolicyPersistenceActor can send to itself to take a Snapshot if the Policy was modified.
     */
    private static final class TakeSnapshotInternal {

        /**
         * The single instance of this message.
         */
        public static final TakeSnapshotInternal INSTANCE = new TakeSnapshotInternal();

        private TakeSnapshotInternal() {}

    }

    /**
     * This strategy handles the {@link RetrievePolicy} command.
     */
    @NotThreadSafe
    private abstract class WithIdReceiveStrategy<T extends WithId> extends AbstractReceiveStrategy<T> {

        /**
         * Constructs a new {@code WithIdReceiveStrategy} object.
         *
         * @param theMatchingClass the class of the message this strategy reacts to.
         * @param theLogger the logger to use for logging.
         * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
         */
        WithIdReceiveStrategy(final Class<T> theMatchingClass, final DiagnosticLoggingAdapter theLogger) {
            super(theMatchingClass, theLogger);
        }

        @Override
        public FI.TypedPredicate<T> getPredicate() {
            return command -> Objects.equals(policyId, command.getId());
        }

    }

    /**
     * This strategy handles the {@link CreatePolicy} command for a new Policy.
     */
    @NotThreadSafe
    private final class CreatePolicyStrategy extends WithIdReceiveStrategy<CreatePolicy> {

        /**
         * Constructs a new {@code CreatePolicyStrategy} object.
         */
        CreatePolicyStrategy() {
            super(CreatePolicy.class, log);
        }

        @Override
        protected void doApply(final CreatePolicy command) {
            // Policy not yet created - do so ..
            final Policy newPolicy = command.getPolicy();
            final PolicyBuilder newPolicyBuilder = PoliciesModelFactory.newPolicyBuilder(newPolicy);
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            if (!newPolicy.getLifecycle().isPresent()) {
                newPolicyBuilder.setLifecycle(PolicyLifecycle.ACTIVE);
            }

            final Policy newPolicyWithLifecycle = newPolicyBuilder.build();
            final PoliciesValidator validator = PoliciesValidator.newInstance(newPolicyWithLifecycle);

            if (validator.isValid()) {
                final PolicyCreated policyCreated =
                        PolicyCreated.of(newPolicyWithLifecycle, getNextRevision(), getEventTimestamp(), dittoHeaders);

                processEvent(policyCreated, event -> {
                    notifySender(CreatePolicyResponse.of(policyId, PolicyPersistenceActor.this.policy, dittoHeaders));
                    log.debug("Created new Policy with ID <{}>.", policyId);
                    becomePolicyCreatedHandler();
                });
            } else {
                policyInvalid(validator.getReason().orElse(null), dittoHeaders);
            }
        }

        @Override
        public FI.UnitApply<CreatePolicy> getUnhandledFunction() {
            return command -> {
                final String msgTemplate = "This Policy Actor did not handle the requested Policy with ID <{0}>!";
                throw new IllegalArgumentException(MessageFormat.format(msgTemplate, command.getId()));
            };
        }

    }

    /**
     * This strategy handles the {@link CreatePolicy} command for an already existing Policy.
     */
    @NotThreadSafe
    private final class PolicyConflictStrategy extends AbstractReceiveStrategy<CreatePolicy> {

        /**
         * Constructs a new {@code PolicyConflictStrategy} object.
         */
        public PolicyConflictStrategy() {
            super(CreatePolicy.class, log);
        }

        @Override
        public FI.TypedPredicate<CreatePolicy> getPredicate() {
            return command -> Objects.equals(policyId, command.getId());
        }

        @Override
        protected void doApply(final CreatePolicy command) {
            notifySender(PolicyConflictException.newBuilder(command.getId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build());
        }

        @Override
        public FI.UnitApply<CreatePolicy> getUnhandledFunction() {
            return command -> {
                final String msgTemplate = "This Policy Actor did not handle the requested Policy with ID <{0}>!";
                throw new IllegalArgumentException(MessageFormat.format(msgTemplate, command.getId()));
            };
        }

    }

    /**
     * This strategy handles the {@link ModifyPolicy} command for an already existing Policy.
     */
    @NotThreadSafe
    private final class ModifyPolicyStrategy extends WithIdReceiveStrategy<ModifyPolicy> {

        /**
         * Constructs a new {@code ModifyPolicyStrategy} object.
         */
        ModifyPolicyStrategy() {
            super(ModifyPolicy.class, log);
        }

        @Override
        protected void doApply(final ModifyPolicy command) {
            final Policy modifiedPolicy = command.getPolicy();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            final PoliciesValidator validator = PoliciesValidator.newInstance(modifiedPolicy);

            if (validator.isValid()) {
                final PolicyModified policyModified =
                        PolicyModified.of(modifiedPolicy, getNextRevision(), getEventTimestamp(), dittoHeaders);
                processEvent(policyModified,
                        event -> notifySender(ModifyPolicyResponse.modified(policyId, dittoHeaders)));
            } else {
                policyInvalid(validator.getReason().orElse(null), dittoHeaders);
            }
        }

        @Override
        public FI.UnitApply<ModifyPolicy> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link RetrievePolicy} command.
     */
    @NotThreadSafe
    private final class RetrievePolicyStrategy extends WithIdReceiveStrategy<RetrievePolicy> {

        /**
         * Constructs a new {@code RetrievePolicyStrategy} object.
         */
        RetrievePolicyStrategy() {
            super(RetrievePolicy.class, log);
        }

        @Override
        protected void doApply(final RetrievePolicy command) {
            notifySender(RetrievePolicyResponse.of(policyId, policy, command.getDittoHeaders()));
        }

        @Override
        public FI.UnitApply<RetrievePolicy> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link DeletePolicy} command.
     */
    @NotThreadSafe
    private final class DeletePolicyStrategy extends WithIdReceiveStrategy<DeletePolicy> {

        /**
         * Constructs a new {@code DeletePolicyStrategy} object.
         */
        DeletePolicyStrategy() {
            super(DeletePolicy.class, log);
        }

        @Override
        protected void doApply(final DeletePolicy command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final PolicyDeleted policyDeleted = PolicyDeleted.of(policyId, getNextRevision(), getEventTimestamp(),
                    dittoHeaders);

            processEvent(policyDeleted, event -> {
                notifySender(DeletePolicyResponse.of(policyId, dittoHeaders));
                log.info("Deleted Policy with ID <{}>.", policyId);
                becomePolicyDeletedHandler();
            });
        }

        @Override
        public FI.UnitApply<DeletePolicy> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link ModifyPolicyEntries} command.
     */
    @NotThreadSafe
    private final class ModifyPolicyEntriesStrategy extends WithIdReceiveStrategy<ModifyPolicyEntries> {

        /**
         * Constructs a new {@code ModifyPolicyEntriesStrategy} object.
         */
        ModifyPolicyEntriesStrategy() {
            super(ModifyPolicyEntries.class, log);
        }

        @Override
        protected void doApply(final ModifyPolicyEntries command) {
            final Iterable<PolicyEntry> policyEntries = command.getPolicyEntries();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            final ModifyPolicyEntriesResponse response = ModifyPolicyEntriesResponse.of(policyId, dittoHeaders);
            final PolicyEntriesModified policyEntriesModified = PolicyEntriesModified.of(policyId, policyEntries,
                    getNextRevision(), getEventTimestamp(), dittoHeaders);

            processEvent(policyEntriesModified, event -> notifySender(response));
        }

        @Override
        public FI.UnitApply<ModifyPolicyEntries> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link ModifyPolicyEntry} command.
     */
    @NotThreadSafe
    private final class ModifyPolicyEntryStrategy extends WithIdReceiveStrategy<ModifyPolicyEntry> {

        /**
         * Constructs a new {@code ModifyPolicyEntryStrategy} object.
         */
        ModifyPolicyEntryStrategy() {
            super(ModifyPolicyEntry.class, log);
        }

        @Override
        protected void doApply(final ModifyPolicyEntry command) {
            final PolicyEntry policyEntry = command.getPolicyEntry();
            final Label label = policyEntry.getLabel();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            final PoliciesValidator validator = PoliciesValidator.newInstance(policy.setEntry(policyEntry));

            if (validator.isValid()) {
                final PolicyEvent eventToPersist;
                final ModifyPolicyEntryResponse response;
                if (policy.contains(label)) {
                    eventToPersist =
                            PolicyEntryModified.of(policyId, policyEntry, getNextRevision(), getEventTimestamp(),
                                    dittoHeaders);
                    response = ModifyPolicyEntryResponse.modified(policyId, dittoHeaders);
                } else {
                    eventToPersist =
                            PolicyEntryCreated.of(policyId, policyEntry, getNextRevision(), getEventTimestamp(),
                                    dittoHeaders);
                    response = ModifyPolicyEntryResponse.created(policyId, policyEntry, dittoHeaders);
                }

                processEvent(eventToPersist, event -> notifySender(response));
            } else {
                policyEntryInvalid(label, validator.getReason().orElse(null), dittoHeaders);
            }
        }

        @Override
        public FI.UnitApply<ModifyPolicyEntry> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link DeletePolicyEntry} command.
     */
    @NotThreadSafe
    private final class DeletePolicyEntryStrategy extends WithIdReceiveStrategy<DeletePolicyEntry> {

        /**
         * Constructs a new {@code DeleteAclEntryStrategy} object.
         */
        DeletePolicyEntryStrategy() {
            super(DeletePolicyEntry.class, log);
        }

        @Override
        protected void doApply(final DeletePolicyEntry command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Label label = command.getLabel();

            if (policy.contains(label)) {
                final PoliciesValidator validator = PoliciesValidator.newInstance(policy.removeEntry(label));

                if (validator.isValid()) {
                    deletePolicyEntry(label, dittoHeaders);
                } else {
                    policyEntryInvalid(label, validator.getReason().orElse(null), dittoHeaders);
                }
            } else {
                policyEntryNotFound(label, dittoHeaders);
            }
        }

        private void deletePolicyEntry(final Label label, final DittoHeaders dittoHeaders) {
            final PolicyEntryDeleted policyEntryDeleted =
                    PolicyEntryDeleted.of(policyId, label, getNextRevision(), getEventTimestamp(), dittoHeaders);

            processEvent(policyEntryDeleted,
                    event -> notifySender(DeletePolicyEntryResponse.of(policyId, label, dittoHeaders)));
        }

        @Override
        public FI.UnitApply<DeletePolicyEntry> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link RetrievePolicyEntries} command.
     */
    @NotThreadSafe
    private final class RetrievePolicyEntriesStrategy extends WithIdReceiveStrategy<RetrievePolicyEntries> {

        /**
         * Constructs a new {@code RetrievePolicyEntryStrategy} object.
         */
        RetrievePolicyEntriesStrategy() {
            super(RetrievePolicyEntries.class, log);
        }

        @Override
        protected void doApply(final RetrievePolicyEntries command) {
            notifySender(RetrievePolicyEntriesResponse.of(policyId, policy.getEntriesSet(), command.getDittoHeaders()));
        }

        @Override
        public FI.UnitApply<RetrievePolicyEntries> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link RetrievePolicyEntry} command.
     */
    @NotThreadSafe
    private final class RetrievePolicyEntryStrategy extends WithIdReceiveStrategy<RetrievePolicyEntry> {

        /**
         * Constructs a new {@code RetrievePolicyEntryStrategy} object.
         */
        RetrievePolicyEntryStrategy() {
            super(RetrievePolicyEntry.class, log);
        }

        @Override
        protected void doApply(final RetrievePolicyEntry command) {
            final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(command.getLabel());
            if (optionalEntry.isPresent()) {
                notifySender(RetrievePolicyEntryResponse.of(policyId, optionalEntry.get(), command.getDittoHeaders()));
            } else {
                policyEntryNotFound(command.getLabel(), command.getDittoHeaders());
            }
        }

        @Override
        public FI.UnitApply<RetrievePolicyEntry> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link ModifySubjects} command.
     */
    @NotThreadSafe
    private final class ModifySubjectsStrategy extends WithIdReceiveStrategy<ModifySubjects> {

        /**
         * Constructs a new {@code ModifySubjectsStrategy} object.
         */
        ModifySubjectsStrategy() {
            super(ModifySubjects.class, log);
        }

        @Override
        protected void doApply(final ModifySubjects command) {
            final Label label = command.getLabel();
            final Subjects subjects = command.getSubjects();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            if (policy.getEntryFor(label).isPresent()) {
                final PoliciesValidator validator =
                        PoliciesValidator.newInstance(policy.setSubjectsFor(label, subjects));

                if (validator.isValid()) {
                    final SubjectsModified subjectsModified =
                            SubjectsModified.of(policyId, label, subjects, getNextRevision(), getEventTimestamp(),
                                    command.getDittoHeaders());
                    processEvent(subjectsModified,
                            event -> notifySender(ModifySubjectsResponse.of(policyId, label, dittoHeaders)));
                } else {
                    policyEntryInvalid(label, validator.getReason().orElse(null), dittoHeaders);
                }
            } else {
                policyEntryNotFound(label, dittoHeaders);
            }
        }

        @Override
        public FI.UnitApply<ModifySubjects> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link RetrieveSubjects} command.
     */
    @NotThreadSafe
    private final class RetrieveSubjectsStrategy extends WithIdReceiveStrategy<RetrieveSubjects> {

        /**
         * Constructs a new {@code RetrieveSubjectsStrategy} object.
         */
        RetrieveSubjectsStrategy() {
            super(RetrieveSubjects.class, log);
        }

        @Override
        protected void doApply(final RetrieveSubjects command) {
            final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(command.getLabel());
            if (optionalEntry.isPresent()) {
                notifySender(
                        RetrieveSubjectsResponse.of(policyId, command.getLabel(), optionalEntry.get().getSubjects(),
                                command.getDittoHeaders()));
            } else {
                policyEntryNotFound(command.getLabel(), command.getDittoHeaders());
            }
        }

        @Override
        public FI.UnitApply<RetrieveSubjects> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link ModifySubject} command.
     */
    @NotThreadSafe
    private final class ModifySubjectStrategy extends WithIdReceiveStrategy<ModifySubject> {

        /**
         * Constructs a new {@code ModifySubjectStrategy} object.
         */
        ModifySubjectStrategy() {
            super(ModifySubject.class, log);
        }

        @Override
        protected void doApply(final ModifySubject command) {
            final Label label = command.getLabel();
            final Subject subject = command.getSubject();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(label);
            if (optionalEntry.isPresent()) {
                final PolicyEntry policyEntry = optionalEntry.get();
                final PoliciesValidator validator = PoliciesValidator.newInstance(policy.setSubjectFor(label, subject));

                if (validator.isValid()) {
                    final PolicyEvent eventToPersist;
                    final ModifySubjectResponse response;

                    if (policyEntry.getSubjects().getSubject(subject.getId()).isPresent()) {
                        response = ModifySubjectResponse.modified(policyId, label, dittoHeaders);
                        eventToPersist =
                                SubjectModified.of(policyId, label, subject, getNextRevision(), getEventTimestamp(),
                                        command.getDittoHeaders());
                    } else {
                        response = ModifySubjectResponse.created(policyId, label, subject, dittoHeaders);
                        eventToPersist =
                                SubjectCreated.of(policyId, label, subject, getNextRevision(), getEventTimestamp(),
                                        command.getDittoHeaders());
                    }

                    processEvent(eventToPersist, event -> notifySender(response));
                } else {
                    policyEntryInvalid(label, validator.getReason().orElse(null), dittoHeaders);
                }
            } else {
                policyEntryNotFound(label, dittoHeaders);
            }
        }

        @Override
        public FI.UnitApply<ModifySubject> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link DeleteSubject} command.
     */
    @NotThreadSafe
    private final class DeleteSubjectStrategy extends WithIdReceiveStrategy<DeleteSubject> {

        /**
         * Constructs a new {@code DeleteSubjectStrategy} object.
         */
        DeleteSubjectStrategy() {
            super(DeleteSubject.class, log);
        }

        @Override
        protected void doApply(final DeleteSubject command) {
            final Label label = command.getLabel();
            final SubjectId subjectId = command.getSubjectId();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(label);
            if (optionalEntry.isPresent()) {
                final PolicyEntry policyEntry = optionalEntry.get();
                if (policyEntry.getSubjects().getSubject(subjectId).isPresent()) {
                    final PoliciesValidator validator =
                            PoliciesValidator.newInstance(policy.removeSubjectFor(label, subjectId));

                    if (validator.isValid()) {
                        final SubjectDeleted subjectDeleted =
                                SubjectDeleted.of(policyId, label, subjectId, getNextRevision(), getEventTimestamp(),
                                        dittoHeaders);

                        processEvent(subjectDeleted,
                                event -> notifySender(DeleteSubjectResponse.of(policyId, label, subjectId,
                                        dittoHeaders)));
                    } else {
                        policyEntryInvalid(label, validator.getReason().orElse(null), dittoHeaders);
                    }
                } else {
                    subjectNotFound(label, subjectId, dittoHeaders);
                }
            } else {
                policyEntryNotFound(label, dittoHeaders);
            }
        }

        @Override
        public FI.UnitApply<DeleteSubject> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link RetrieveSubject} command.
     */
    @NotThreadSafe
    private final class RetrieveSubjectStrategy extends WithIdReceiveStrategy<RetrieveSubject> {

        /**
         * Constructs a new {@code RetrieveSubjectStrategy} object.
         */
        RetrieveSubjectStrategy() {
            super(RetrieveSubject.class, log);
        }

        @Override
        protected void doApply(final RetrieveSubject command) {
            final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(command.getLabel());
            if (optionalEntry.isPresent()) {
                final PolicyEntry policyEntry = optionalEntry.get();
                final Optional<Subject> optionalSubject = policyEntry.getSubjects().getSubject(command.getSubjectId());
                if (optionalSubject.isPresent()) {
                    notifySender(RetrieveSubjectResponse.of(policyId, command.getLabel(),
                            optionalSubject.get(), command.getDittoHeaders()));
                } else {
                    subjectNotFound(command.getLabel(), command.getSubjectId(), command.getDittoHeaders());
                }
            } else {
                policyEntryNotFound(command.getLabel(), command.getDittoHeaders());
            }
        }

        @Override
        public FI.UnitApply<RetrieveSubject> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link ModifyResources} command.
     */
    @NotThreadSafe
    private final class ModifyResourcesStrategy extends WithIdReceiveStrategy<ModifyResources> {

        /**
         * Constructs a new {@code ModifyResourcesStrategy} object.
         */
        ModifyResourcesStrategy() {
            super(ModifyResources.class, log);
        }

        @Override
        protected void doApply(final ModifyResources command) {
            final Label label = command.getLabel();
            final Resources resources = command.getResources();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            if (policy.getEntryFor(label).isPresent()) {
                final PoliciesValidator validator =
                        PoliciesValidator.newInstance(policy.setResourcesFor(label, resources));

                if (validator.isValid()) {
                    final ResourcesModified resourcesModified =
                            ResourcesModified.of(policyId, label, resources, getNextRevision(), getEventTimestamp(),
                                    dittoHeaders);

                    processEvent(resourcesModified,
                            event -> notifySender(ModifyResourcesResponse.of(policyId, label, dittoHeaders)));
                } else {
                    policyEntryInvalid(label, validator.getReason().orElse(null), dittoHeaders);
                }
            } else {
                policyEntryNotFound(label, dittoHeaders);
            }
        }

        @Override
        public FI.UnitApply<ModifyResources> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link RetrieveResources} command.
     */
    @NotThreadSafe
    private final class RetrieveResourcesStrategy extends WithIdReceiveStrategy<RetrieveResources> {

        /**
         * Constructs a new {@code RetrieveResourcesStrategy} object.
         */
        RetrieveResourcesStrategy() {
            super(RetrieveResources.class, log);
        }

        @Override
        protected void doApply(final RetrieveResources command) {
            final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(command.getLabel());
            if (optionalEntry.isPresent()) {
                notifySender(RetrieveResourcesResponse.of(policyId, command.getLabel(),
                        optionalEntry.get().getResources(), command.getDittoHeaders()));
            } else {
                policyEntryNotFound(command.getLabel(), command.getDittoHeaders());
            }
        }

        @Override
        public FI.UnitApply<RetrieveResources> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link ModifyResource} command.
     */
    @NotThreadSafe
    private final class ModifyResourceStrategy extends WithIdReceiveStrategy<ModifyResource> {

        /**
         * Constructs a new {@code ModifyResourceStrategy} object.
         */
        ModifyResourceStrategy() {
            super(ModifyResource.class, log);
        }

        @Override
        protected void doApply(final ModifyResource command) {
            final Label label = command.getLabel();
            final Resource resource = command.getResource();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(label);
            if (optionalEntry.isPresent()) {
                final PoliciesValidator validator =
                        PoliciesValidator.newInstance(policy.setResourceFor(label, resource));

                if (validator.isValid()) {
                    final PolicyEntry policyEntry = optionalEntry.get();
                    final PolicyEvent eventToPersist;
                    final ModifyResourceResponse response;

                    if (policyEntry.getResources().getResource(resource.getResourceKey()).isPresent()) {
                        response = ModifyResourceResponse.modified(policyId, label, dittoHeaders);
                        eventToPersist =
                                ResourceModified.of(policyId, label, resource, getNextRevision(), getEventTimestamp(),
                                        dittoHeaders);
                    } else {
                        response = ModifyResourceResponse.created(policyId, label, resource, dittoHeaders);
                        eventToPersist =
                                ResourceCreated.of(policyId, label, resource, getNextRevision(), getEventTimestamp(),
                                        dittoHeaders);
                    }

                    processEvent(eventToPersist, event -> notifySender(response));
                } else {
                    policyEntryInvalid(label, validator.getReason().orElse(null), dittoHeaders);
                }
            } else {
                policyEntryNotFound(label, dittoHeaders);
            }
        }

        @Override
        public FI.UnitApply<ModifyResource> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link DeleteResource} command.
     */
    @NotThreadSafe
    private final class DeleteResourceStrategy extends WithIdReceiveStrategy<DeleteResource> {

        /**
         * Constructs a new {@code DeleteResourceStrategy} object.
         */
        DeleteResourceStrategy() {
            super(DeleteResource.class, log);
        }

        @Override
        protected void doApply(final DeleteResource command) {
            final Label label = command.getLabel();
            final ResourceKey resourceKey = command.getResourceKey();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(label);
            if (optionalEntry.isPresent()) {
                final PolicyEntry policyEntry = optionalEntry.get();

                if (policyEntry.getResources().getResource(resourceKey).isPresent()) {
                    final PoliciesValidator validator =
                            PoliciesValidator.newInstance(policy.removeResourceFor(label, resourceKey));

                    if (validator.isValid()) {
                        final ResourceDeleted resourceDeleted =
                                ResourceDeleted.of(policyId, label, resourceKey, getNextRevision(), getEventTimestamp(),
                                        dittoHeaders);

                        processEvent(resourceDeleted,
                                event -> notifySender(DeleteResourceResponse.of(policyId, label, resourceKey,
                                        dittoHeaders)));
                    } else {
                        policyEntryInvalid(label, validator.getReason().orElse(null), dittoHeaders);
                    }
                } else {
                    resourceNotFound(label, resourceKey, dittoHeaders);
                }
            } else {
                policyEntryNotFound(label, dittoHeaders);
            }
        }

        @Override
        public FI.UnitApply<DeleteResource> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link RetrieveResource} command.
     */
    @NotThreadSafe
    private final class RetrieveResourceStrategy extends WithIdReceiveStrategy<RetrieveResource> {

        /**
         * Constructs a new {@code RetrieveResourceStrategy} object.
         */
        RetrieveResourceStrategy() {
            super(RetrieveResource.class, log);
        }

        @Override
        protected void doApply(final RetrieveResource command) {
            final Optional<PolicyEntry> optionalEntry = policy.getEntryFor(command.getLabel());
            if (optionalEntry.isPresent()) {
                final PolicyEntry policyEntry = optionalEntry.get();

                final Optional<Resource> optionalResource =
                        policyEntry.getResources().getResource(command.getResourceKey());
                if (optionalResource.isPresent()) {
                    notifySender(RetrieveResourceResponse.of(policyId, command.getLabel(), optionalResource.get(),
                            command.getDittoHeaders()));
                } else {
                    resourceNotFound(command.getLabel(), command.getResourceKey(), command.getDittoHeaders());
                }
            } else {
                policyEntryNotFound(command.getLabel(), command.getDittoHeaders());
            }
        }

        @Override
        public FI.UnitApply<RetrieveResource> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link SudoRetrievePolicy} command w/o valid authorization context.
     */
    @NotThreadSafe
    private final class SudoRetrievePolicyStrategy extends WithIdReceiveStrategy<SudoRetrievePolicy> {

        /**
         * Constructs a new {@code SudoRetrievePolicyStrategy} object.
         */
        SudoRetrievePolicyStrategy() {
            super(SudoRetrievePolicy.class, log);
        }

        @Override
        protected void doApply(final SudoRetrievePolicy command) {
            notifySender(SudoRetrievePolicyResponse.of(policyId, policy, command.getDittoHeaders()));
        }

        @Override
        public FI.UnitApply<SudoRetrievePolicy> getUnhandledFunction() {
            return command -> notifySender(policyNotFound(command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the success of saving a snapshot by logging the Policy's ID.
     */
    @NotThreadSafe
    private final class SaveSnapshotSuccessStrategy extends AbstractReceiveStrategy<SaveSnapshotSuccess> {

        /**
         * Constructs a new {@code SaveSnapshotSuccessStrategy} object.
         */
        SaveSnapshotSuccessStrategy() {
            super(SaveSnapshotSuccess.class, log);
        }

        @Override
        protected void doApply(final SaveSnapshotSuccess message) {
            final SnapshotMetadata snapshotMetadata = message.metadata();
            log.debug("Snapshot taken for Policy <{}> with metadata <{}>.", policyId, snapshotMetadata);

            final long newSnapShotSequenceNumber = snapshotMetadata.sequenceNr();
            if (newSnapShotSequenceNumber <= lastSnapshotSequenceNr) {
                log.warning("Policy <{}> has been already snap-shot with a newer or equal sequence number." +
                                " Last sequence number: <{}>, new snapshot metadata: <{}>.", policyId,
                        lastSnapshotSequenceNr, snapshotMetadata);
                resetSnapshotInProgress();
            } else {
                deleteSnapshot(lastSnapshotSequenceNr);
                deleteEventsOlderThan(newSnapShotSequenceNumber);

                lastSnapshotSequenceNr = newSnapShotSequenceNumber;

                resetSnapshotInProgress();
            }
        }

        private void deleteEventsOlderThan(final long newestSequenceNumber) {
            if (eventsDeleteOld && newestSequenceNumber > 1) {
                final long upToSequenceNumber = newestSequenceNumber - 1;
                log.debug("Delete all event messages for Policy <{}> up to sequence number <{}>.", policy,
                        upToSequenceNumber);
                deleteMessages(upToSequenceNumber);
            }
        }

        private void deleteSnapshot(final long sequenceNumber) {
            if (snapshotDeleteOld && sequenceNumber != -1) {
                log.debug("Delete old snapshot for Policy <{}> with sequence number <{}>.", policyId, sequenceNumber);
                PolicyPersistenceActor.this.deleteSnapshot(sequenceNumber);
            }
        }

        private void resetSnapshotInProgress() {
            if (invokeAfterSnapshotRunnable != null) {
                invokeAfterSnapshotRunnable.run();
                invokeAfterSnapshotRunnable = null;
            }
            snapshotInProgress = false;
        }

    }

    /**
     * This strategy handles the failure of saving a snapshot by logging an error.
     */
    @NotThreadSafe
    private final class SaveSnapshotFailureStrategy extends AbstractReceiveStrategy<SaveSnapshotFailure> {

        /**
         * Constructs a new {@code SaveSnapshotFailureStrategy} object.
         */
        SaveSnapshotFailureStrategy() {
            super(SaveSnapshotFailure.class, log);
        }

        @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
        @Override
        protected void doApply(final SaveSnapshotFailure message) {
            final Throwable cause = message.cause();
            final String causeMessage = cause.getMessage();
            if (isPolicyDeleted()) {
                log.error(cause, "Failed to save snapshot for delete operation of <{}>. Cause: {}.", policyId,
                        causeMessage);
            } else {
                log.error(cause, "Failed to save snapshot for <{}>. Cause: {}.", policyId, causeMessage);
            }
        }
        
    }

    /**
     * This strategy handles the success of deleting a snapshot by logging an info.
     */
    @NotThreadSafe
    private final class DeleteSnapshotSuccessStrategy extends AbstractReceiveStrategy<DeleteSnapshotSuccess> {

        /**
         * Constructs a new {@code DeleteSnapshotSuccessStrategy} object.
         */
        DeleteSnapshotSuccessStrategy() {
            super(DeleteSnapshotSuccess.class, log);
        }

        @Override
        protected void doApply(final DeleteSnapshotSuccess message) {
            log.debug("Deleting snapshot with sequence number <{}> for Policy <{}> was successful.",
                    message.metadata().sequenceNr(), policyId);
        }

    }

    /**
     * This strategy handles the failure of deleting a snapshot by logging an error.
     */
    @NotThreadSafe
    private final class DeleteSnapshotFailureStrategy extends AbstractReceiveStrategy<DeleteSnapshotFailure> {

        /**
         * Constructs a new {@code DeleteSnapshotFailureStrategy} object.
         */
        DeleteSnapshotFailureStrategy() {
            super(DeleteSnapshotFailure.class, log);
        }

        @Override
        protected void doApply(final DeleteSnapshotFailure message) {
            final Throwable cause = message.cause();
            log.error(cause, "Deleting snapshot with sequence number <{}> for Policy <{}> failed. Cause {}: {}",
                    message.metadata().sequenceNr(), policyId, cause.getClass().getSimpleName(), cause.getMessage());
        }

    }

    /**
     * This strategy handles the success of deleting messages by logging an info.
     */
    @NotThreadSafe
    private final class DeleteMessagesSuccessStrategy extends AbstractReceiveStrategy<DeleteMessagesSuccess> {

        /**
         * Constructs a new {@code DeleteMessagesSuccessStrategy} object.
         */
        DeleteMessagesSuccessStrategy() {
            super(DeleteMessagesSuccess.class, log);
        }

        @Override
        protected void doApply(final DeleteMessagesSuccess message) {
            log.debug("Deleting messages for Policy <{}> was successful.", policyId);
        }

    }

    /**
     * This strategy handles the failure of deleting messages by logging an error.
     */
    @NotThreadSafe
    private final class DeleteMessagesFailureStrategy extends AbstractReceiveStrategy<DeleteMessagesFailure> {

        /**
         * Constructs a new {@code DeleteMessagesFailureStrategy} object.
         */
        DeleteMessagesFailureStrategy() {
            super(DeleteMessagesFailure.class, log);
        }

        @Override
        protected void doApply(final DeleteMessagesFailure message) {
            final Throwable cause = message.cause();
            log.error(cause, "Deleting messages up to sequence number <{}> for Policy <{}> failed. Cause {}: {}",
                    policyId, message.toSequenceNr(), cause.getClass().getSimpleName(), cause.getMessage());
        }

    }

    /**
     * This strategy handles all commands which were not explicitly handled beforehand. Those commands are logged as
     * unknown messages and are marked as unhandled.
     */
    @NotThreadSafe
    private final class MatchAnyAfterInitializeStrategy extends AbstractReceiveStrategy<Object> {

        /**
         * Constructs a new {@code MatchAnyAfterInitializeStrategy} object.
         */
        MatchAnyAfterInitializeStrategy() {
            super(Object.class, log);
        }

        @Override
        protected void doApply(final Object message) {
            log.warning("Unknown message: {}", message);
            unhandled(message);
        }

    }

    /**
     * This strategy handles all messages which were received before the Policy was initialized. Those messages are
     * logged
     * as unexpected messages and cause the actor to be stopped.
     */
    @NotThreadSafe
    private final class MatchAnyDuringInitializeStrategy extends AbstractReceiveStrategy<Object> {

        /**
         * Constructs a new {@code MatchAnyDuringInitializeStrategy} object.
         */
        MatchAnyDuringInitializeStrategy() {
            super(Object.class, log);
        }

        @Override
        protected void doApply(final Object message) {
            log.debug("Unexpected message after initialization of actor received: <{}> - " +
                            "Terminating this actor and sending <{}> to requester..", message,
                    PolicyNotAccessibleException.class.getName());
            final PolicyNotAccessibleException.Builder builder = PolicyNotAccessibleException.newBuilder(policyId);
            if (message instanceof WithDittoHeaders) {
                builder.dittoHeaders(((WithDittoHeaders) message).getDittoHeaders());
            }
            notifySender(builder.build());

            // Make sure activity checker is on, but there is no need to schedule it more than once.
            if (activityChecker == null) {
                scheduleCheckForPolicyActivity(activityCheckInterval.getSeconds());
            }
        }

    }

    /**
     * This strategy handles any messages for a previous deleted Policy.
     */
    @NotThreadSafe
    private final class PolicyNotFoundStrategy extends AbstractReceiveStrategy<Object> {

        /**
         * Constructs a new {@code PolicyNotFoundStrategy} object.
         */
        PolicyNotFoundStrategy() {
            super(Object.class, log);
        }

        @Override
        protected void doApply(final Object message) {
            final PolicyNotAccessibleException.Builder builder = PolicyNotAccessibleException.newBuilder(policyId);
            if (message instanceof WithDittoHeaders) {
                builder.dittoHeaders(((WithDittoHeaders) message).getDittoHeaders());
            }
            notifySender(builder.build());
        }

    }

    /**
     * This strategy handles the {@link CheckForActivity} message which checks for activity of the Actor and
     * terminates
     * itself if there was no activity since the last check.
     */
    @NotThreadSafe
    private final class CheckForActivityStrategy extends AbstractReceiveStrategy<CheckForActivity> {

        /**
         * Constructs a new {@code CheckForActivityStrategy} object.
         */
        CheckForActivityStrategy() {
            super(CheckForActivity.class, log);
        }

        @Override
        protected void doApply(final CheckForActivity message) {
            if (isPolicyDeleted() && lastSnapshotSequenceNr < lastSequenceNr()) {
                // take a snapshot after a period of inactivity if:
                // - thing is deleted,
                // - the latest snapshot is out of date or is still ongoing.
                final Object snapshotToStore = snapshotAdapter.toSnapshotStore(policy);
                saveSnapshot(snapshotToStore);
                scheduleCheckForPolicyActivity(activityCheckDeletedInterval.getSeconds());
            } else if (accessCounter > message.getCurrentAccessCounter()) {
                // if the Thing was accessed in any way since the last check
                scheduleCheckForPolicyActivity(activityCheckInterval.getSeconds());
            } else {
                // safe to shutdown after a period of inactivity if:
                // - policy is active (and taking regular snapshots of itself), or
                // - policy is deleted and the latest snapshot is up to date
                if (isPolicyActive()) {
                    shutdown("Policy <{}> was not accessed in a while. Shutting Actor down ...", policyId);
                } else {
                    shutdown("Policy <{}> was deleted recently. Shutting Actor down ...", policyId);
                }
            }
        }

        private void shutdown(final String shutdownLogTemplate, final String thingId) {
            log.debug(shutdownLogTemplate, thingId);
            // stop the supervisor (otherwise it'd restart this actor) which causes this actor to stop, too.
            getContext().getParent().tell(PoisonPill.getInstance(), getSelf());
        }

    }

    /**
     * This strategy handles the {@link TakeSnapshotInternal} message which checks for the need to take a snapshot
     * and
     * does so if needed.
     */
    @NotThreadSafe
    private final class TakeSnapshotInternalStrategy extends AbstractTakeSnapshotStrategy<TakeSnapshotInternal> {

        /**
         * Constructs a new {@code TakeSnapshotInternalStrategy} object.
         */
        TakeSnapshotInternalStrategy() {
            super(TakeSnapshotInternal.class);
        }

        @Override
        void onCompleted(final TakeSnapshotInternal requestMessage, final ActorRef requestSender,
                final boolean snapshotCreated) {

            log.debug("Completed internal request for snapshot: snapshotCreated={}", snapshotCreated);
        }

    }

    /**
     * Abstract base class for handling snapshot requests.
     */
    @NotThreadSafe
    private abstract class AbstractTakeSnapshotStrategy<T> extends AbstractReceiveStrategy<T> {

        AbstractTakeSnapshotStrategy(final Class<T> clazz) {
            super(clazz, log);
        }

        /**
         * Hook for reacting on completion of taking the snapshot.
         *
         * @param requestMessage the request message
         * @param requestSender the sender of the request message
         * @param snapshotCreated whether a snapshot has actually been created; may be {@code false}, if there was
         * already an existing snapshot for the latest sequence number
         */
        abstract void onCompleted(final T requestMessage, final ActorRef requestSender, boolean snapshotCreated);

        @Override
        protected void doApply(final T message) {
            log.debug("Received request to SaveSnapshot. Message: {}", message);
            final ActorRef sender = getSender();
            // if there was any modifying activity since the last taken snapshot:
            if (lastSequenceNr() > lastSnapshotSequenceNr) {
                doSaveSnapshot(() -> onCompleted(message, sender, true));
            } else {
                // if a snapshot already exists, don't take another one and signal success
                onCompleted(message, sender, false);
            }

            // if the Policy is not "deleted":
            if (isPolicyActive()) {
                // schedule the next snapshot:
                scheduleSnapshot(snapshotInterval.getSeconds());
            }
        }

    }

}
