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
package org.eclipse.ditto.internal.utils.persistentactors;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.api.commands.sudo.SudoCommand;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.internal.utils.akka.actors.AbstractActorWithStashWithTimers;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyEnforcer;
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyEnforcerCacheLoader;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cacheloaders.config.DefaultAskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionProxyActorFactory;
import org.eclipse.ditto.internal.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.internal.utils.cluster.config.DefaultClusterConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.policies.api.PoliciesMessagingConstants;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.enforcement.EnforcementReloaded;
import org.eclipse.ditto.policies.model.PolicyId;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Abstract enforcer of commands performing authorization / enforcement of incoming signals
 * targeting to be handled by either the {@link AbstractPersistenceActor} of incoming live signals to be published to
 * pub/sub.
 *
 * @param <I> TODO TJ
 * @param <C> the base type of the Commands this actor handles
 * @param <R> TODO TJ
 */
public abstract class AbstractEnforcerActor<I extends EntityId, C extends Command<?>, R extends CommandResponse<?>>
        extends AbstractActorWithStashWithTimers {

    /**
     * Timeout for local actor invocations - a small timeout should be more than sufficient as those are just method
     * calls.
     */
    protected static final Duration DEFAULT_LOCAL_ASK_TIMEOUT = Duration.ofSeconds(5);

    protected final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    protected final I entityId;
    private final EnforcementReloaded<C, R> enforcement;

    @Nullable private AsyncCacheLoader<EnforcementCacheKey, Entry<PolicyEnforcer>> policyEnforcerCacheLoader;

    @Nullable private PolicyId policyIdForEnforcement;
    @Nullable private PolicyEnforcer policyEnforcer;


    protected AbstractEnforcerActor(final I entityId, final EnforcementReloaded<C, R> enforcement,
            final ActorRef pubSubMediator) {

        this.entityId = entityId;
        this.enforcement = enforcement;

        // subscribe for PolicyTags in order to reload policyEnforcer when "backing policy" was modified
        pubSubMediator.tell(DistPubSubAccess.subscribe(PolicyTag.PUB_SUB_TOPIC_INVALIDATE_ENFORCERS, getSelf()),
                getSelf());
    }

    /**
     * @return ID of the Policy which should be used for enforcement.
     */
    protected abstract CompletionStage<PolicyId> getPolicyIdForEnforcement();

    /**
     * TODO TJ doc
     * TODO TJ make abstract and move the current implementation to ThingsEnforcerActor only
     */
    protected CompletionStage<PolicyEnforcer> loadPolicyEnforcer(final PolicyId policyId) {
        final ActorSystem actorSystem = getContext().getSystem();
        if (null == policyEnforcerCacheLoader) {
            final ClusterConfig clusterConfig =
                    DefaultClusterConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
            final var shardRegionProxyActorFactory = ShardRegionProxyActorFactory.newInstance(
                    actorSystem, clusterConfig);
            final var policiesShardRegionProxy = shardRegionProxyActorFactory.getShardRegionProxyActor(
                    PoliciesMessagingConstants.CLUSTER_ROLE,
                    PoliciesMessagingConstants.SHARD_REGION);

            // TODO TJ configure + load correctly
            final AskWithRetryConfig askWithRetryConfig = DefaultAskWithRetryConfig.of(ConfigFactory.empty(), "foo");

            // TODO TJ maybe pass in the loader as constructor arg instead?
            policyEnforcerCacheLoader = new PolicyEnforcerCacheLoader(askWithRetryConfig, actorSystem.getScheduler(),
                    policiesShardRegionProxy);
        }

        // TODO TJ use explicit executor instead of taking up resources on the main dispatcher!
        try {
            return policyEnforcerCacheLoader.asyncLoad(EnforcementCacheKey.of(policyId), actorSystem.dispatcher())
                    .thenApply(entry -> {
                        if (entry.exists()) {
                            return entry.getValueOrThrow();
                        } else {
                            return null; // TODO TJ?
                        }
                    });
        } catch (final Exception e) {
            throw new RuntimeException(e); // TODO TJ
        }

    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        reloadPolicyEnforcer();
    }

    @SuppressWarnings("unchecked")
    protected Receive activeBehaviour() {
        return ReceiveBuilder.create()
                .match(PolicyTag.class, pt -> pt.getEntityId().equals(policyIdForEnforcement),
                        this::refreshPolicyEnforcerAfterReceivedMatchingPolicyTag)
                .match(SudoCommand.class, sudoCommand -> log.withCorrelationId(sudoCommand)
                        .warning("Received SudoCommand in enforcer which should never happen"))
                .match(Command.class, c -> enforce((C) c))
                .match(CommandResponse.class, r -> filter((R) r))
                .matchAny(message ->
                        log.withCorrelationId(
                                        message instanceof WithDittoHeaders withDittoHeaders ? withDittoHeaders : null)
                                .warning("Got unknown message: '{}'", message))
                .build();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(DistributedPubSubMediator.SubscribeAck.class, s -> log.debug("Got subscribeAck <{}>.", s))
                .matchEquals(Control.INIT_DONE, initDone -> {
                    unstashAll();
                    becomeActive();
                })
                .matchAny(this::handleMessagesDuringStartup)
                .build();
    }

    private void reloadPolicyEnforcer() {
        final ActorRef self = getSelf();
        getPolicyIdForEnforcement()
                .thenCompose(policyId -> {
                    this.policyIdForEnforcement = policyId;
                    return loadPolicyEnforcer(policyId);
                })
                .whenComplete((pEnf, throwable) -> {
                    if (null != throwable) {
                        log.error(throwable, "Failed to load policy enforcer; stopping myself..");
                        getContext().stop(getSelf());
                    } else {
                        policyEnforcer = pEnf; // note that policyEnforcer might be null afterwards if it could not be loaded!
                        self.tell(Control.INIT_DONE, self);
                    }
                });
    }

    private void becomeActive() {
        getContext().become(activeBehaviour());
    }

    private void handleMessagesDuringStartup(final Object message) {
        stash();
        log.withCorrelationId(message instanceof WithDittoHeaders withDittoHeaders ? withDittoHeaders : null)
                .info("Stashed received message during startup of enforcer actor: <{}>",
                        message.getClass().getSimpleName());
    }

    private void refreshPolicyEnforcerAfterReceivedMatchingPolicyTag(final PolicyTag policyTag) {
        reloadPolicyEnforcer();
    }

    /**
     * Enforce all commands using the {@code enforcement} of this actor.
     * Successfully enforced commands are sent back to the {@code sender()} - which is our parent, the Supervisor.
     * Our parent is responsible for then forwarding the command to the persistence actor.
     */
    private void enforce(final C command) {
        if (command.getCategory() == Command.Category.QUERY && !command.getDittoHeaders().isResponseRequired()) {
            // ignore query command with response-required=false
            return;
        }

        try {
            final C authorizedCommand;
            if (null != policyEnforcer) {
                authorizedCommand = enforcement.authorizeSignal(command, policyEnforcer);
            } else {
                authorizedCommand = enforcement.authorizeSignalWithMissingEnforcer(command);
            }
            // sender is our dear parent
            getSender().tell(authorizedCommand, getSelf());
        } catch (final DittoRuntimeException dittoRuntimeException) {
            // sender is our dear parent
            getSender().tell(dittoRuntimeException, getSelf());
        }
    }

    private void filter(final R commandResponse) {
        if (null != policyEnforcer) {
            getSender().tell(enforcement.filterResponse(commandResponse, policyEnforcer), getContext().getParent());
        } else {
            log.error("Could not filter commandResponse because policyEnforcer was missing");
        }
    }


    /**
     * Control message for the enforcer actor.
     */
    public enum Control {

        /**
         * Signals initialization is done, enforcement can be performed.
         */
        INIT_DONE
    }

}
