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
package org.eclipse.ditto.policies.service.persistence.actors;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.service.actors.ShutdownBehaviour;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.internal.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceSupervisor;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyUnavailableException;
import org.eclipse.ditto.policies.service.common.config.DittoPoliciesConfig;
import org.eclipse.ditto.policies.service.common.config.PolicyAnnouncementConfig;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;
import org.eclipse.ditto.policies.service.enforcement.PolicyCommandEnforcement;
import org.eclipse.ditto.policies.service.persistence.actors.announcements.PolicyAnnouncementManager;

import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Supervisor for {@link org.eclipse.ditto.policies.service.persistence.actors.PolicyPersistenceActor} which means it will create, start and watch it as child actor.
 * <p>
 * If the child terminates, it will wait for the calculated exponential back-off time and restart it afterwards.
 * Between the termination of the child and the restart, this actor answers to all requests with a {@link
 * PolicyUnavailableException} as fail fast strategy.
 */
public final class PolicySupervisorActor extends AbstractPersistenceSupervisor<PolicyId, PolicyCommand<?>> {

    private final ActorRef pubSubMediator;
    private final SnapshotAdapter<Policy> snapshotAdapter;
    private final ActorRef announcementManager;
    private final PolicyConfig policyConfig;

    @SuppressWarnings("unused")
    private PolicySupervisorActor(final ActorRef pubSubMediator,
            final SnapshotAdapter<Policy> snapshotAdapter,
            final DistributedPub<PolicyAnnouncement<?>> policyAnnouncementPub,
            @Nullable final BlockedNamespaces blockedNamespaces) {

        super(blockedNamespaces, DEFAULT_LOCAL_ASK_TIMEOUT);
        this.pubSubMediator = pubSubMediator;
        this.snapshotAdapter = snapshotAdapter;
        final DittoPoliciesConfig policiesConfig = DittoPoliciesConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        policyConfig = policiesConfig.getPolicyConfig();

        final var props =
                getAnnouncementManagerProps(policyAnnouncementPub, policyConfig.getPolicyAnnouncementConfig());
        if (props != null) {
            announcementManager = getContext().actorOf(props, "am");
        } else {
            announcementManager = getContext().getSystem().deadLetters();
        }
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
     * @param policyAnnouncementPub publisher interface of policy announcements.
     * @param blockedNamespaces the blocked namespaces functionality to retrieve/subscribe for blocked namespaces.
     * @return the {@link Props} to create this actor.
     */
    public static Props props(final ActorRef pubSubMediator,
            final SnapshotAdapter<Policy> snapshotAdapter,
            final DistributedPub<PolicyAnnouncement<?>> policyAnnouncementPub,
            @Nullable final BlockedNamespaces blockedNamespaces) {

        return Props.create(PolicySupervisorActor.class, pubSubMediator, snapshotAdapter, policyAnnouncementPub,
                blockedNamespaces);
    }

    @Override
    protected PolicyId getEntityId() throws Exception {
        return PolicyId.of(URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8.name()));
    }

    @Override
    protected Props getPersistenceActorProps(final PolicyId entityId) {
        return PolicyPersistenceActor.props(entityId, snapshotAdapter, pubSubMediator, announcementManager,
                policyConfig);
    }

    @Override
    protected Props getPersistenceEnforcerProps(final PolicyId entityId) {
        return PolicyEnforcerActor.props(entityId, new PolicyCommandEnforcement(getContext().system()));
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
        final PolicyId policyId = entityId != null ? entityId : PolicyId.of("UNKNOWN:ID");
        return PolicyUnavailableException.newBuilder(policyId);
    }

    @Nullable
    private Props getAnnouncementManagerProps(final DistributedPub<PolicyAnnouncement<?>> pub,
            final PolicyAnnouncementConfig policyAnnouncementConfig) {
        try {
            final PolicyId policyId = getEntityId();
            return PolicyAnnouncementManager.props(policyId, pub, getSelf(), policyAnnouncementConfig);
        } catch (final Exception e) {
            log.error(e, "Failed to determine entity ID; becoming corrupted.");
            becomeCorrupted();
            return null;
        }
    }

}
