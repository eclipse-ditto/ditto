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
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyEnforcer;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.enforcement.EnforcementReloaded;
import org.eclipse.ditto.policies.model.PolicyId;

import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Abstract enforcer of commands performing authorization / enforcement of incoming signals
 * targeting to be handled by either the {@link AbstractPersistenceActor} of incoming live signals to be published to
 * pub/sub.
 *
 * @param <I> the type of the EntityId this enforcer actor enforces commands for.
 * @param <C> the type of the Commands this enforcer actor enforces.
 * @param <R> the type of the CommandResponses this enforcer actor filters.
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

    @Nullable protected PolicyId policyIdForEnforcement;
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
     * Provides the {@link PolicyId} to use for the policy enforcement.
     * The implementation chooses the most efficient strategy to retrieve it.
     *
     * @return a successful CompletionStage of either the loaded {@link PolicyId} of the Policy which should be used
     * for enforcement or a failed CompletionStage with the cause for the failure.
     */
    protected abstract CompletionStage<PolicyId> providePolicyIdForEnforcement();

    /**
     * Provides the {@link PolicyEnforcer} instance (which holds a {@code Policy} + the built {@code Enforcer}) for the
     * provided {@code policyId} asynchronously.
     * The implementation chooses the most efficient strategy to retrieve it.
     *
     * @param policyId the {@link PolicyId} to retrieve the PolicyEnforcer for.
     * @return a successful CompletionStage of either the loaded {@link PolicyEnforcer} or a failed CompletionStage with
     * the cause for the failure.
     */
    protected abstract CompletionStage<PolicyEnforcer> providePolicyEnforcer(PolicyId policyId);

    @Override
    public void preStart() throws Exception {
        super.preStart();
        reloadPolicyEnforcer();
    }

    @SuppressWarnings("unchecked")
    protected Receive activeBehaviour() {
        return ReceiveBuilder.create()
                .match(DistributedPubSubMediator.SubscribeAck.class, s -> log.debug("Got subscribeAck <{}>.", s))
                .match(PolicyTag.class, pt -> pt.getEntityId().equals(policyIdForEnforcement),
                        this::refreshPolicyEnforcerAfterReceivedMatchingPolicyTag)
                .match(SudoCommand.class, sudoCommand -> log.withCorrelationId(sudoCommand)
                        .error("Received SudoCommand in enforcer which should never happen: <{}>", sudoCommand)
                )
                .match(Command.class, c -> enforceCommand((C) c))
                .match(CommandResponse.class, r -> filterResponse((R) r))
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
        providePolicyIdForEnforcement()
                .thenCompose(policyId -> {
                    this.policyIdForEnforcement = policyId;
                    return providePolicyEnforcer(policyId);
                })
                .whenComplete((pEnf, throwable) -> {
                    if (null != throwable) {
                        policyEnforcer = null;
                        log.error(throwable, "Failed to load policy enforcer; stopping myself..");
                        getContext().stop(getSelf());
                    } else if (null != pEnf) {
                        policyEnforcer = pEnf;
                        self.tell(Control.INIT_DONE, self);
                    } else {
                        // TODO TJ complete with exception?? or what to do? terminate?
                        policyEnforcer = null;
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
     * Enforces the passed {@code command} using the {@code enforcement} of this actor.
     * Successfully enforced commands are sent back to the {@code getSender()} - which is our dear parent, the Supervisor.
     * Our parent is responsible for then forwarding the command to the persistence actor.
     *
     * @param command the {@code Command} to enforce based in the {@code policyEnforcer}.
     */
    private void enforceCommand(final C command) {
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
            log.withCorrelationId(authorizedCommand)
                    .info("Completed enforcement of message type <{}> with outcome 'success'",
                            authorizedCommand.getType());
            getSender().tell(authorizedCommand, getSelf());
        } catch (final DittoRuntimeException dittoRuntimeException) {
            log.withCorrelationId(dittoRuntimeException)
                    .info("Completed enforcement of message type <{}> with outcome 'failed' and headers: <{}>",
                            command.getType(), command.getDittoHeaders());
            getSender().tell(dittoRuntimeException, getSelf());
        }
    }

    /**
     * Filters the response payload of the passed {@code commandResponse} using the {@code enforcement} of this actor.
     * Filtered command responses are sent back to the {@code getSender()} - which is our dear parent, the Supervisor.
     * Our parent is responsible for then forwarding the command response to the original sender.
     *
     * @param commandResponse the {@code CommandResponse} to filter based in the {@code policyEnforcer}.
     */
    private void filterResponse(final R commandResponse) {
        if (null != policyEnforcer) {
            final R filteredResponse = enforcement.filterResponse(commandResponse, policyEnforcer);
            log.withCorrelationId(filteredResponse)
                    .info("Completed filtering of command response type <{}>", filteredResponse.getType());
            getSender().tell(filteredResponse, getContext().getParent());
        } else {
            log.withCorrelationId(commandResponse)
                    .error("Could not filter command response because policyEnforcer was missing");
        }
    }


    /**
     * Control message for the enforcer actor.
     */
    public enum Control {

        /**
         * Initialization is done, enforcement can be performed.
         */
        INIT_DONE
    }

}
