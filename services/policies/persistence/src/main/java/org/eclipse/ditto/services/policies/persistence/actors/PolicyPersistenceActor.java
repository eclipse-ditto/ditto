/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.policies.persistence.actors;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.PolicyLifecycle;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectExpiry;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.policies.common.config.DittoPoliciesConfig;
import org.eclipse.ditto.services.policies.common.config.PolicyConfig;
import org.eclipse.ditto.services.policies.persistence.actors.strategies.commands.PolicyCommandStrategies;
import org.eclipse.ditto.services.policies.persistence.actors.strategies.events.PolicyEventStrategies;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.services.utils.persistence.mongo.config.ActivityCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.SnapshotConfig;
import org.eclipse.ditto.services.utils.persistentactors.AbstractShardedPersistenceActor;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.services.utils.persistentactors.commands.DefaultContext;
import org.eclipse.ditto.services.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.services.utils.pubsub.DistributedPub;
import org.eclipse.ditto.signals.announcements.policies.PolicyAnnouncement;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.policies.SubjectDeleted;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.RecoveryCompleted;

/**
 * PersistentActor which "knows" the state of a single {@link Policy}.
 */
public final class PolicyPersistenceActor
        extends AbstractShardedPersistenceActor<Command<?>, Policy, PolicyId, PolicyId, PolicyEvent<?>> {

    /**
     * The prefix of the persistenceId for Policies.
     */
    public static final String PERSISTENCE_ID_PREFIX = "policy:";

    /**
     * The ID of the journal plugin this persistence actor uses.
     */
    static final String JOURNAL_PLUGIN_ID = "akka-contrib-mongodb-persistence-policies-journal";

    /**
     * The ID of the snapshot plugin this persistence actor uses.
     */
    static final String SNAPSHOT_PLUGIN_ID = "akka-contrib-mongodb-persistence-policies-snapshots";

    private static final String NEXT_SUBJECT_EXPIRY_TIMER = "next-subject-expiry-timer";

    private final ActorRef pubSubMediator;
    private final DistributedPub<PolicyAnnouncement<?>> policyAnnouncementPub;
    private final PolicyConfig policyConfig;

    PolicyPersistenceActor(final PolicyId policyId,
            final SnapshotAdapter<Policy> snapshotAdapter,
            final ActorRef pubSubMediator,
            final DistributedPub<PolicyAnnouncement<?>> policyAnnouncementPub) {
        super(policyId, snapshotAdapter);
        this.pubSubMediator = pubSubMediator;
        this.policyAnnouncementPub = policyAnnouncementPub;
        final DittoPoliciesConfig policiesConfig = DittoPoliciesConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        this.policyConfig = policiesConfig.getPolicyConfig();
    }

    /**
     * Creates Akka configuration object {@link Props} for this PolicyPersistenceActor.
     *
     * @param policyId the ID of the Policy this Actor manages.
     * @param snapshotAdapter the adapter to serialize Policy snapshots.
     * @param pubSubMediator the PubSub mediator actor.
     * @param policyAnnouncementPub the publisher interface for policy announcements.
     * @return the Akka configuration Props object
     */
    public static Props props(final PolicyId policyId,
            final SnapshotAdapter<Policy> snapshotAdapter,
            final ActorRef pubSubMediator,
            final DistributedPub<PolicyAnnouncement<?>> policyAnnouncementPub) {

        return Props.create(PolicyPersistenceActor.class, policyId, snapshotAdapter, pubSubMediator,
                policyAnnouncementPub);
    }

    @Override
    public String persistenceId() {
        return PERSISTENCE_ID_PREFIX + entityId;
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
    protected Class<?> getEventClass() {
        return PolicyEvent.class;
    }

    @Override
    protected CommandStrategy.Context<PolicyId> getStrategyContext() {
        return DefaultContext.getInstance(entityId, log);
    }

    @Override
    protected PolicyCommandStrategies getCreatedStrategy() {
        return PolicyCommandStrategies.getInstance(policyConfig, getContext().getSystem());
    }

    @Override
    protected CommandStrategy<? extends Command<?>, Policy, PolicyId, PolicyEvent<?>> getDeletedStrategy() {
        return PolicyCommandStrategies.getCreatePolicyStrategy(policyConfig);
    }

    @Override
    protected EventStrategy<PolicyEvent<?>, Policy> getEventStrategy() {
        return PolicyEventStrategies.getInstance();
    }

    @Override
    protected ActivityCheckConfig getActivityCheckConfig() {
        return policyConfig.getActivityCheckConfig();
    }

    @Override
    protected SnapshotConfig getSnapshotConfig() {
        return policyConfig.getSnapshotConfig();
    }

    @Override
    protected boolean entityExistsAsDeleted() {
        return null != entity && entity.hasLifecycle(PolicyLifecycle.DELETED);
    }

    @Override
    protected DittoRuntimeExceptionBuilder<?> newNotAccessibleExceptionBuilder() {
        return PolicyNotAccessibleException.newBuilder(entityId);
    }

    @Override
    protected void publishEvent(final PolicyEvent<?> event) {
        pubSubMediator.tell(DistPubSubAccess.publishViaGroup(PolicyEvent.TYPE_PREFIX, event), getSender());

        final boolean policyEnforcerInvalidatedPreemptively = Boolean.parseBoolean(event.getDittoHeaders()
                .getOrDefault(DittoHeaderDefinition.POLICY_ENFORCER_INVALIDATED_PREEMPTIVELY.getKey(),
                        Boolean.FALSE.toString()));
        if (!policyEnforcerInvalidatedPreemptively) {
            final PolicyTag policyTag = PolicyTag.of(entityId, event.getRevision());
            pubSubMediator.tell(DistPubSubAccess.publish(PolicyTag.PUB_SUB_TOPIC_INVALIDATE_ENFORCERS, policyTag),
                    getSender());
        }
    }

    @Override
    protected JsonSchemaVersion getEntitySchemaVersion(final Policy entity) {
        return entity.getImplementedSchemaVersion();
    }

    @Override
    protected Receive matchAnyAfterInitialization() {
        return ReceiveBuilder.create()
                .matchEquals(DeleteOldestExpiredSubject.INSTANCE, d -> handleDeleteExpiredSubjects())
                .matchAny(message -> log.warning("Unknown message: {}", message))
                .build();
    }

    @Override
    protected void recoveryCompleted(final RecoveryCompleted event) {
        if (entity != null) {
            // if we have an expired subject at this point (after recovery), it must first be removed before e.g.
            //  sending back the policy to a SudoRetrievePolicy command from concierge
            handleDeleteExpiredSubjects();
            becomeCreatedOrDeletedHandler();
        }
    }

    @Override
    protected void onEntityModified() {
        scheduleNextSubjectExpiryCheck();
    }

    private void scheduleNextSubjectExpiryCheck() {
        findEarliestSubjectExpiryTimestamp(entity).ifPresent(earliestSubjectExpiryTimestamp -> {
            timers().cancel(NEXT_SUBJECT_EXPIRY_TIMER);
            final Instant earliestExpiry = earliestSubjectExpiryTimestamp.getTimestamp();
            final Duration durationBetweenNowAndEarliestExpiry = Duration.between(Instant.now(), earliestExpiry);
            if (durationBetweenNowAndEarliestExpiry.isNegative()) {
                // there are currently expired subjects, so delete the oldest right away:
                getSelf().tell(DeleteOldestExpiredSubject.INSTANCE, getSelf());
            } else {
                final Duration oneDay = Duration.ofDays(1);
                final Duration scheduleTimeout = durationBetweenNowAndEarliestExpiry.compareTo(oneDay) < 0 ?
                        durationBetweenNowAndEarliestExpiry : oneDay;
                log.info("Scheduling message for deleting next expired subject in: <{}> - " +
                        "earliest expiry is at: <{}>", scheduleTimeout, earliestExpiry);
                timers().startSingleTimer(NEXT_SUBJECT_EXPIRY_TIMER, DeleteOldestExpiredSubject.INSTANCE,
                        scheduleTimeout);
            }
        });
    }

    private void handleDeleteExpiredSubjects() {
        log.debug("Calculating whether subjects did expire and need to be deleted..");
        calculateSubjectDeletedEventOfOldestExpiredSubject(entityId, entity)
                .ifPresentOrElse(subjectDeleted ->
                                persistAndApplyEvent(subjectDeleted, (persistedEvent, resultingEntity) ->
                                        log.withCorrelationId(persistedEvent)
                                                .info("Deleted expired subject <{}> of label <{}>",
                                                        subjectDeleted.getSubjectId(), subjectDeleted.getLabel())
                                ),
                        this::scheduleNextSubjectExpiryCheck
                );
    }

    private Optional<SubjectDeleted> calculateSubjectDeletedEventOfOldestExpiredSubject(final PolicyId policyId,
            @Nullable final Iterable<PolicyEntry> policyEntries) {

        return determineAlreadyExpiredSubjects(policyEntries)
                .min(Comparator.comparing(pair -> pair.second().getExpiry().orElseThrow()))
                .map(pair -> {
                    final Instant eventTimestamp = Instant.now();
                    final DittoHeaders eventDittoHeaders = DittoHeaders.newBuilder()
                            .correlationId(UUID.randomUUID().toString())
                            .build();
                    return SubjectDeleted.of(policyId,
                            pair.first(),
                            pair.second().getId(),
                            getRevisionNumber() + 1L,
                            eventTimestamp,
                            eventDittoHeaders
                    );
                });
    }

    private static Stream<Pair<Label, Subject>> determineAlreadyExpiredSubjects(
            @Nullable final Iterable<PolicyEntry> entries) {

        return determineSubjectsWithExpiry(entries)
                .filter(pair -> pair.second().getExpiry()
                        .map(SubjectExpiry::isExpired)
                        .orElse(false)
                );
    }

    private static Stream<Pair<Label, Subject>> determineSubjectsWithExpiry(
            @Nullable final Iterable<PolicyEntry> entries) {

        if (null == entries) {
            return Stream.empty();
        }
        return StreamSupport.stream(entries.spliterator(), false)
                .flatMap(entry -> entry.getSubjects().stream()
                        .map(subject -> Pair.create(entry.getLabel(), subject))
                )
                .filter(pair -> pair.second().getExpiry().isPresent());
    }

    private static Optional<SubjectExpiry> findEarliestSubjectExpiryTimestamp(
            @Nullable final Iterable<PolicyEntry> policyEntries) {

        if (null == policyEntries) {
            return Optional.empty();
        }

        return StreamSupport.stream(policyEntries.spliterator(), false)
                .map(PolicyEntry::getSubjects)
                .flatMap(Subjects::stream)
                .map(Subject::getExpiry)
                .flatMap(Optional::stream)
                .min(Comparator.comparing(SubjectExpiry::getTimestamp));
    }

    private static final class DeleteOldestExpiredSubject {

        private static final DeleteOldestExpiredSubject INSTANCE = new DeleteOldestExpiredSubject();
    }
}
