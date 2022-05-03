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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.api.commands.sudo.SudoCommand;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.internal.utils.akka.actors.AbstractActorWithStashWithTimers;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.enforcement.EnforcementReloaded;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;

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
 * @param <E> the type of the EnforcementReloaded this enforcer actor uses for doing command enforcements.
 */
public abstract class AbstractEnforcerActor<I extends EntityId, C extends Command<?>, R extends CommandResponse<?>,
        E extends EnforcementReloaded<C, R>>
        extends AbstractActorWithStashWithTimers {

    /**
     * Timeout for local actor invocations - a small timeout should be more than sufficient as those are just method
     * calls.
     */
    protected static final Duration DEFAULT_LOCAL_ASK_TIMEOUT = Duration.ofSeconds(5);

    protected final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    protected final I entityId;
    protected final E enforcement;

    @Nullable protected PolicyId policyIdForEnforcement;
    @Nullable protected PolicyEnforcer policyEnforcer;


    protected AbstractEnforcerActor(final I entityId, final E enforcement, final ActorRef pubSubMediator) {

        this.entityId = entityId;
        this.enforcement = enforcement;
        enforcement.registerPolicyEnforcerLoader(this::providePolicyEnforcer);
        enforcement.registerPolicyInjectionConsumer(this::injectPolicy);

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
    protected abstract CompletionStage<PolicyEnforcer> providePolicyEnforcer(@Nullable PolicyId policyId);

    /**
     * Accepts the passed in {@code injectedPolicy} in order to e.g. update the {@code policyEnforcer} this enforcer
     * actor uses for doing policy enforcement.
     *
     * @param injectedPolicy the Policy to inject.
     */
    protected void injectPolicy(final Policy injectedPolicy) {
        this.policyEnforcer = PolicyEnforcer.of(injectedPolicy, PolicyEnforcers.defaultEvaluator(injectedPolicy));
    }

    /**
     * Determines whether for the passed in {@code command} the {@code policyEnforcer} of this enforcer actor should be
     * invalidated **after** doing the enforcement (because it changed/could have changed) the authorization logic.
     *
     * @param command the command to check.
     * @return {@code true} if the Policy Enforcer should be invalidated after the enforcement.
     */
    protected abstract boolean shouldInvalidatePolicyEnforcerAfterEnforcement(C command);

    @Override
    public void preStart() throws Exception {
        super.preStart();
        final ActorRef self = getSelf();
        reloadPolicyEnforcer(loadedPolicyEnforcer -> {
            this.policyEnforcer = loadedPolicyEnforcer;
            self.tell(Control.INIT_DONE, self);
        });
    }

    @SuppressWarnings("unchecked")
    protected Receive activeBehaviour() {
        return ReceiveBuilder.create()
                .match(DistributedPubSubMediator.SubscribeAck.class, s -> log.debug("Got subscribeAck <{}>.", s))
                .match(PolicyTag.class, pt -> pt.getEntityId().equals(policyIdForEnforcement),
                    pt -> performPolicyEnforcerReload()
                )
                .match(PolicyTag.class, pt -> {
                    // ignore policy tags not intended for this actor - not necessary to log on debug!
                })
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

    private void reloadPolicyEnforcer(final Consumer<PolicyEnforcer> successConsumer) {
        providePolicyIdForEnforcement()
                .thenCompose(policyId -> {
                    this.policyIdForEnforcement = policyId; // policyId could be null, e.g. if entity is not yet existing
                    return providePolicyEnforcer(policyId);
                })
                .whenComplete((pEnf, throwable) -> {
                    if (null != throwable) {
                        policyEnforcer = null;
                        log.error(throwable, "Failed to load policy enforcer; stopping myself..");
                        getContext().stop(getSelf());
                    } else if (null != pEnf) {
                        policyEnforcer = pEnf;
                        successConsumer.accept(pEnf);
                    } else {
                        policyEnforcer = null;
                        successConsumer.accept(null);
                    }
                });
    }

    private void becomeAwaitingInitialization() {
        getContext().become(createReceive());
    }

    private void becomeActive() {
        getContext().become(activeBehaviour());
    }

    private void handleMessagesDuringStartup(final Object message) {
        stash();
        log.withCorrelationId(message instanceof WithDittoHeaders withDittoHeaders ? withDittoHeaders : null)
                .debug("Stashed received message during startup of enforcer actor: <{}>",
                        message.getClass().getSimpleName());
    }

    private void performPolicyEnforcerReload() {
        final ActorRef self = getSelf();
        reloadPolicyEnforcer(loadedPolicyEnforcer -> {
            this.policyEnforcer = loadedPolicyEnforcer;
            self.tell(Control.INIT_DONE, self);
        });
        becomeAwaitingInitialization();
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

        doEnforceCommand(command, getSender())
                .thenAccept(successfullyEnforced -> {
                    if (shouldReloadAfterEnforcement(command, successfullyEnforced)) {
                        // trigger reloading the policy
                        performPolicyEnforcerReload();
                    }
                })
                .toCompletableFuture()
                .join(); // block on the actor's dispatcher in order to guarantee in-order processing and blocking the inbox
    }

    private boolean shouldReloadAfterEnforcement(final C command, final boolean successfullyEnforced) {
        return shouldInvalidatePolicyEnforcerAfterEnforcement(command) ||
                (successfullyEnforced && null == policyEnforcer);
    }

    private CompletionStage<Boolean> doEnforceCommand(final C command,
            final ActorRef sender) {
        final ActorRef self = getSelf();
        try {
            final CompletionStage<C> authorizedCommandStage;
            if (null != policyEnforcer) {
                authorizedCommandStage = enforcement.authorizeSignal(command, policyEnforcer);
            } else {
                authorizedCommandStage = enforcement.authorizeSignalWithMissingEnforcer(command);
            }

            return authorizedCommandStage.handle((authorizedCommand, throwable) -> {
                final boolean successfullyEnforced;
                if (null != authorizedCommand) {
                    log.withCorrelationId(authorizedCommand)
                            .info("Completed enforcement of message type <{}> with outcome 'success'",
                                    authorizedCommand.getType());
                    sender.tell(authorizedCommand, self);
                    successfullyEnforced = true;
                } else if (null != throwable) {
                    final DittoRuntimeException dittoRuntimeException =
                            DittoRuntimeException.asDittoRuntimeException(throwable, t ->
                                    DittoInternalErrorException.newBuilder()
                                            .cause(t)
                                            .dittoHeaders(command.getDittoHeaders())
                                            .build()
                            );
                    log.withCorrelationId(dittoRuntimeException)
                            .info("Completed enforcement of message type <{}> with outcome 'failed' and headers: <{}>",
                                    command.getType(), command.getDittoHeaders());
                    sender.tell(dittoRuntimeException, self);
                    successfullyEnforced = false;
                } else {
                    log.withCorrelationId(command)
                            .error("Neither authorizedCommand nor throwable were present during enforcement of command: " +
                                    "<{}>", command);
                    successfullyEnforced = false;
                }
                return successfullyEnforced;
            });
        } catch (final DittoRuntimeException dittoRuntimeException) {
            log.withCorrelationId(dittoRuntimeException)
                    .info("Completed enforcement of message type <{}> with outcome 'failed' and headers: <{}>",
                            command.getType(), command.getDittoHeaders());
            sender.tell(dittoRuntimeException, self);
            return CompletableFuture.completedStage(false);
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
        final ActorRef sender = getSender();
        final ActorRef parent = getContext().getParent();

        if (enforcement.shouldFilterCommandResponses()) {
            if (null != policyEnforcer) {
                try {
                    final CompletionStage<R> filteredResponseStage =
                            enforcement.filterResponse(commandResponse, policyEnforcer);
                    filteredResponseStage.whenComplete((filteredResponse, throwable) -> {
                        if (null != filteredResponse) {
                            log.withCorrelationId(filteredResponse)
                                    .info("Completed filtering of command response type <{}>", filteredResponse.getType());
                            sender.tell(filteredResponse, parent);
                        } else if (null != throwable) {
                            final DittoRuntimeException dittoRuntimeException =
                                    DittoRuntimeException.asDittoRuntimeException(throwable, t ->
                                            DittoInternalErrorException.newBuilder()
                                                    .cause(t)
                                                    .dittoHeaders(commandResponse.getDittoHeaders())
                                                    .build()
                                    );
                            log.withCorrelationId(dittoRuntimeException)
                                    .info("Exception during filtering of command response type <{}> and headers: <{}>",
                                            commandResponse.getType(), commandResponse.getDittoHeaders());
                            sender.tell(dittoRuntimeException, parent);
                        } else {
                            log.withCorrelationId(commandResponse)
                                    .error("Neither filteredResponse nor throwable were present during filtering of " +
                                            "commandResponse: <{}>", commandResponse);
                        }
                    });
                } catch (final DittoRuntimeException dittoRuntimeException) {
                    log.withCorrelationId(dittoRuntimeException)
                            .info("Exception during filtering of command response type <{}> and headers: <{}>",
                                    commandResponse.getType(), commandResponse.getDittoHeaders());
                    sender.tell(dittoRuntimeException, parent);
                }
            } else {
                log.withCorrelationId(commandResponse)
                        .error("Could not filter command response because policyEnforcer was missing");
            }
        } else {
            sender.tell(commandResponse, parent);
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
