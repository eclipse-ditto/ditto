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
package org.eclipse.ditto.services.policies.persistence.actors.policy;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.PolicyLifecycle;
import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.policies.common.config.DittoPoliciesConfig;
import org.eclipse.ditto.services.policies.common.config.PolicyConfig;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.services.utils.persistence.mongo.config.ActivityCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.SnapshotConfig;
import org.eclipse.ditto.services.utils.persistentactors.AbstractShardedPersistenceActor;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.services.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;

/**
 * PersistentActor which "knows" the state of a single {@link Policy}.
 */
public final class PolicyPersistenceActor
        extends AbstractShardedPersistenceActor<Command, Policy, PolicyId, PolicyEvent> {

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

    private final SnapshotAdapter<Policy> snapshotAdapter;
    private final ActorRef pubSubMediator;
    private final PolicyConfig policyConfig;

    PolicyPersistenceActor(final PolicyId policyId,
            final SnapshotAdapter<Policy> snapshotAdapter,
            final ActorRef pubSubMediator) {
        super(policyId, snapshotAdapter);
        this.snapshotAdapter = snapshotAdapter;
        this.pubSubMediator = pubSubMediator;
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
     * @return the Akka configuration Props object
     */
    public static Props props(final PolicyId policyId,
            final SnapshotAdapter<Policy> snapshotAdapter,
            final ActorRef pubSubMediator) {

        return Props.create(PolicyPersistenceActor.class, policyId, snapshotAdapter, pubSubMediator);
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
    protected Class<PolicyEvent> getEventClass() {
        return PolicyEvent.class;
    }

    @Override
    protected CommandStrategy<Command, Policy, PolicyId, Result<PolicyEvent>> getCommandStrategy() {
        return PolicyCommandStrategies.getInstance();
    }

    @Override
    protected CommandStrategy<? extends Command, Policy, PolicyId, Result<PolicyEvent>> getCreateStrategy() {
        return PolicyCommandStrategies.getCreatePolicyStrategy();
    }

    @Override
    protected EventStrategy<PolicyEvent, Policy> getEventStrategy() {
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
    protected DittoRuntimeExceptionBuilder newNotAccessibleExceptionBuilder() {
        return PolicyNotAccessibleException.newBuilder(entityId);
    }

    @Override
    protected void publishEvent(final PolicyEvent event) {
        pubSubMediator.tell(DistPubSubAccess.publishViaGroup(PolicyEvent.TYPE_PREFIX, event), getSelf());
    }

    @Override
    protected JsonSchemaVersion getEntitySchemaVersion(final Policy entity) {
        return entity.getImplementedSchemaVersion();
    }
}
