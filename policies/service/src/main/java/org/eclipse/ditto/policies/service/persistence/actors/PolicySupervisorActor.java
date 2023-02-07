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
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceSupervisor;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
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
 * Supervisor for {@link org.eclipse.ditto.policies.service.persistence.actors.PolicyPersistenceActor} which means
 * it will create, start and watch it as child actor.
 * <p>
 * If the child terminates, it will wait for the calculated exponential back-off time and restart it afterwards.
 * Between the termination of the child and the restart, this actor answers to all requests with a
 * {@link PolicyUnavailableException} as fail fast strategy.
 */
public final class PolicySupervisorActor extends AbstractPersistenceSupervisor<PolicyId, PolicyCommand<?>> {

    private final ActorRef pubSubMediator;
    private final ActorRef announcementManager;
    private final PolicyConfig policyConfig;
    private final PolicyEnforcerProvider policyEnforcerProvider;

    @SuppressWarnings("unused")
    private PolicySupervisorActor(final ActorRef pubSubMediator,
            final DistributedPub<PolicyAnnouncement<?>> policyAnnouncementPub,
            @Nullable final BlockedNamespaces blockedNamespaces,
            final PolicyEnforcerProvider policyEnforcerProvider,
            final MongoReadJournal mongoReadJournal) {

        super(blockedNamespaces, mongoReadJournal, DEFAULT_LOCAL_ASK_TIMEOUT);
        this.policyEnforcerProvider = policyEnforcerProvider;
        this.pubSubMediator = pubSubMediator;
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
     * @param policyAnnouncementPub publisher interface of policy announcements.
     * @param blockedNamespaces the blocked namespaces functionality to retrieve/subscribe for blocked namespaces.
     * @param policyEnforcerProvider used to load the policy enforcer to authorize commands.
     * @param mongoReadJournal the ReadJournal used for gaining access to historical values of the policy.
     * @return the {@link Props} to create this actor.
     */
    public static Props props(final ActorRef pubSubMediator,
            final DistributedPub<PolicyAnnouncement<?>> policyAnnouncementPub,
            @Nullable final BlockedNamespaces blockedNamespaces,
            final PolicyEnforcerProvider policyEnforcerProvider,
            final MongoReadJournal mongoReadJournal) {

        return Props.create(PolicySupervisorActor.class, pubSubMediator,
                policyAnnouncementPub, blockedNamespaces, policyEnforcerProvider, mongoReadJournal);
    }

    @Override
    protected PolicyId getEntityId() throws Exception {
        return PolicyId.of(URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8));
    }

    @Override
    protected Props getPersistenceActorProps(final PolicyId entityId) {
        return PolicyPersistenceActor.props(entityId, mongoReadJournal, pubSubMediator, announcementManager,
                policyConfig);
    }

    @Override
    protected Props getPersistenceEnforcerProps(final PolicyId entityId) {
        return PolicyEnforcerActor.props(entityId, new PolicyCommandEnforcement(), policyEnforcerProvider);
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
