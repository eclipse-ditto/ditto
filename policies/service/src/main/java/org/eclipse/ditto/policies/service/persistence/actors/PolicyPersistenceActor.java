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
package org.eclipse.ditto.policies.service.persistence.actors;

import java.time.Instant;
import java.util.Set;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.ActivityCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.SnapshotConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceActor;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.commands.DefaultContext;
import org.eclipse.ditto.internal.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyLifecycle;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyHistoryNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.DittoPoliciesConfig;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;
import org.eclipse.ditto.policies.service.persistence.actors.strategies.commands.PolicyCommandStrategies;
import org.eclipse.ditto.policies.service.persistence.actors.strategies.events.PolicyEventStrategies;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.persistence.RecoveryCompleted;

/**
 * PersistentActor which "knows" the state of a single {@link Policy}.
 */
public final class PolicyPersistenceActor
        extends AbstractPersistenceActor<Command<?>, Policy, PolicyId, PolicyId, PolicyEvent<?>> {

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

    private final ActorRef pubSubMediator;
    private final PolicyConfig policyConfig;
    private final ActorRef announcementManager;
    private final ActorRef supervisor;

    @SuppressWarnings("unused")
    private PolicyPersistenceActor(final PolicyId policyId,
            final MongoReadJournal mongoReadJournal,
            final ActorRef pubSubMediator,
            final ActorRef announcementManager,
            final PolicyConfig policyConfig) {

        super(policyId, mongoReadJournal);
        this.pubSubMediator = pubSubMediator;
        this.announcementManager = announcementManager;
        this.policyConfig = policyConfig;
        this.supervisor = getContext().getParent();
    }

    private PolicyPersistenceActor(final PolicyId policyId,
            final MongoReadJournal mongoReadJournal,
            final ActorRef pubSubMediator,
            final ActorRef announcementManager,
            final ActorRef supervisor) {

        // not possible to call other constructor because "getContext()" is not available as argument of "this()"
        super(policyId, mongoReadJournal);
        this.pubSubMediator = pubSubMediator;
        this.announcementManager = announcementManager;
        this.supervisor = supervisor;
        final DittoPoliciesConfig policiesConfig = DittoPoliciesConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        this.policyConfig = policiesConfig.getPolicyConfig();
    }

    /**
     * Creates Akka configuration object {@link Props} for this PolicyPersistenceActor.
     *
     * @param policyId the ID of the Policy this Actor manages.
     * @param mongoReadJournal the ReadJournal used for gaining access to historical values of the policy.
     * @param pubSubMediator the PubSub mediator actor.
     * @param announcementManager manager of policy announcements.
     * @param policyConfig the policy config.
     * @return the Akka configuration Props object
     */
    public static Props props(final PolicyId policyId,
            final MongoReadJournal mongoReadJournal,
            final ActorRef pubSubMediator,
            final ActorRef announcementManager,
            final PolicyConfig policyConfig) {

        return Props.create(PolicyPersistenceActor.class, policyId, mongoReadJournal, pubSubMediator,
                announcementManager, policyConfig);
    }

    static Props propsForTests(final PolicyId policyId,
            final MongoReadJournal mongoReadJournal,
            final ActorRef pubSubMediator,
            final ActorRef announcementManager,
            final ActorSystem actorSystem) {

        return Props.create(PolicyPersistenceActor.class, policyId, mongoReadJournal, pubSubMediator,
                announcementManager,
                actorSystem.deadLetters());
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
        return DefaultContext.getInstance(entityId, log, getContext().getSystem());
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
    protected DittoRuntimeExceptionBuilder<?> newHistoryNotAccessibleExceptionBuilder(final long revision) {
        return PolicyHistoryNotAccessibleException.newBuilder(entityId, revision);
    }

    @Override
    protected DittoRuntimeExceptionBuilder<?> newHistoryNotAccessibleExceptionBuilder(final Instant timestamp) {
        return PolicyHistoryNotAccessibleException.newBuilder(entityId, timestamp);
    }

    @Override
    protected void publishEvent(@Nullable final Policy previousEntity, final PolicyEvent<?> event) {

        // always publish the PolicyEvent
        pubSubMediator.tell(DistPubSubAccess.publishViaGroup(PolicyEvent.TYPE_PREFIX, event), getSelf());

        if (null != previousEntity && null != entity) {
            if (previousEntity.hasLifecycle(PolicyLifecycle.DELETED) || entityExistsAsDeleted() ||
                    !previousEntity.isSemanticallySameAs(entity)) {
                // however only publish the PolicyTag when the Policy semantically changed
                // (e.g. not when only a "subject" payload changed but not the subjectId itself)
                publishPolicyTag(event);
            } else {
                log.withCorrelationId(event)
                        .info("NOT publishing PolicyTag in cluster for PolicyEvent as the Policy did not change " +
                                "its semantics for applying enforcement.");
            }
        } else {
            publishPolicyTag(event);
        }
    }

    private void publishPolicyTag(final PolicyEvent<?> event) {

        final PolicyTag policyTag = PolicyTag.of(entityId, event.getRevision());
        pubSubMediator.tell(
                DistPubSubAccess.publishViaGroup(PolicyTag.PUB_SUB_TOPIC_MODIFIED, policyTag),
                getSelf()
        );
        pubSubMediator.tell(
                DistPubSubAccess.publish(PolicyTag.PUB_SUB_TOPIC_INVALIDATE_ENFORCERS, policyTag),
                getSelf()
        );
    }

    @Override
    public void onMutation(final Command<?> command, final PolicyEvent<?> event, final WithDittoHeaders response,
            final boolean becomeCreated, final boolean becomeDeleted) {

        persistAndApplyEvent(event, (persistedEvent, resultingEntity) -> {
            if (shouldSendResponse(command.getDittoHeaders())) {
                notifySender(getSender(), response);
            }
            if (becomeDeleted) {
                becomeDeletedHandler();
            }
            if (becomeCreated) {
                becomeCreatedHandler();
            }
        });
    }

    @Override
    protected JsonSchemaVersion getEntitySchemaVersion(final Policy entity) {
        return entity.getImplementedSchemaVersion();
    }

    @Override
    protected boolean shouldSendResponse(final DittoHeaders dittoHeaders) {
        return dittoHeaders.isResponseRequired();
    }

    @Override
    protected boolean isEntityAlwaysAlive() {
        return isAlwaysAlive(entity);
    }

    @Override
    protected void recoveryCompleted(final RecoveryCompleted event) {
        if (entity != null) {
            announcementManager.tell(entity, ActorRef.noSender());
        }
        super.recoveryCompleted(event);
    }

    @Override
    protected PolicyEvent<?> modifyEventBeforePersist(final PolicyEvent<?> event) {
        final PolicyEvent<?> superEvent = super.modifyEventBeforePersist(event);

        if (willEntityBeAlwaysAlive(event)) {
            final DittoHeaders headersWithJournalTags = superEvent.getDittoHeaders()
                    .toBuilder()
                    .journalTags(Set.of(JOURNAL_TAG_ALWAYS_ALIVE))
                    .build();
            return superEvent.setDittoHeaders(headersWithJournalTags);
        }

        return superEvent;
    }

    @Override
    protected void onEntityModified() {
        if (entity != null) {
            announcementManager.tell(entity, ActorRef.noSender());
        }
    }

    @Override
    protected ActorRef getSudoCommandDoneRecipient() {
        return supervisor;
    }

    private boolean willEntityBeAlwaysAlive(final PolicyEvent<?> policyEvent) {
        return isAlwaysAlive(getEventStrategy().handle(policyEvent, entity, getRevisionNumber()));
    }

    private boolean isAlwaysAlive(@Nullable final Policy policy) {
        if (policy == null) {
            return false;
        } else {
            return StreamSupport.stream(policy.spliterator(), false)
                    .map(PolicyEntry::getSubjects)
                    .flatMap(Subjects::stream)
                    .anyMatch(subject -> subject.getExpiry().isPresent());
        }
    }

}
