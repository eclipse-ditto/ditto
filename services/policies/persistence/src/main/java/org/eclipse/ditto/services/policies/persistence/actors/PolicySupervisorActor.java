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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.entity.id.DefaultNamespacedEntityId;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.services.base.actors.ShutdownBehaviour;
import org.eclipse.ditto.services.base.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.services.policies.common.config.DittoPoliciesConfig;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.services.utils.persistentactors.AbstractPersistenceSupervisor;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyUnavailableException;

import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Supervisor for {@link PolicyPersistenceActor} which means it will create, start and watch it as child actor.
 * <p>
 * If the child terminates, it will wait for the calculated exponential back-off time and restart it afterwards.
 * Between the termination of the child and the restart, this actor answers to all requests with a {@link
 * PolicyUnavailableException} as fail fast strategy.
 */
public final class PolicySupervisorActor extends AbstractPersistenceSupervisor<PolicyId> {

    private final ActorRef pubSubMediator;
    private final SnapshotAdapter<Policy> snapshotAdapter;

    private PolicySupervisorActor(final ActorRef pubSubMediator, final SnapshotAdapter<Policy> snapshotAdapter) {
        this.pubSubMediator = pubSubMediator;
        this.snapshotAdapter = snapshotAdapter;
    }

    /**
     * Props for creating a {@code PolicySupervisorActor}.
     * <p>
     * Exceptions in the child are handled with a supervision strategy that restarts the child on {@link
     * NullPointerException}'s, stops it for {@link ActorKilledException}'s and escalates all others.
     * </p>
     *
     * @param pubSubMediator the PubSub mediator actor.
     * @param snapshotAdapter the adapter to serialize snapshots.
     * @return the {@link Props} to create this actor.
     */
    public static Props props(final ActorRef pubSubMediator, final SnapshotAdapter<Policy> snapshotAdapter) {

        return Props.create(PolicySupervisorActor.class, pubSubMediator, snapshotAdapter);
    }

    @Override
    protected PolicyId getEntityId() throws Exception {
        return PolicyId.of(URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8.name()));
    }

    @Override
    protected Props getPersistenceActorProps(final PolicyId entityId) {
        return PolicyPersistenceActor.props(entityId, snapshotAdapter, pubSubMediator);
    }

    @Override
    protected ExponentialBackOffConfig getExponentialBackOffConfig() {
        final DittoPoliciesConfig policiesConfig = DittoPoliciesConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        return policiesConfig.getPolicyConfig().getSupervisorConfig().getExponentialBackOffConfig();
    }

    @Override
    protected ShutdownBehaviour getShutdownBehaviour(final PolicyId entityId) {
        return ShutdownBehaviour.fromId(entityId, pubSubMediator, getSelf());
    }

    @Override
    protected DittoRuntimeExceptionBuilder<?> getUnavailableExceptionBuilder(@Nullable final PolicyId entityId) {
        final PolicyId policyId = entityId != null ? entityId : PolicyId.of(DefaultNamespacedEntityId.dummy());
        return PolicyUnavailableException.newBuilder(policyId);
    }

}
