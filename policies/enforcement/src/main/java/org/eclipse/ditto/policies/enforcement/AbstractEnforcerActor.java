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
package org.eclipse.ditto.policies.enforcement;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.api.commands.sudo.SudoCommand;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.internal.utils.akka.actors.AbstractActorWithStashWithTimers;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;
import org.eclipse.ditto.policies.enforcement.pre.PreEnforcerProvider;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;

import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;

/**
 * Abstract enforcer of commands performing authorization / enforcement of incoming signals.
 *
 * @param <I> the type of the EntityId this enforcer actor enforces commands for.
 * @param <S> the type of the Signals this enforcer actor enforces.
 * @param <R> the type of the CommandResponses this enforcer actor filters.
 * @param <E> the type of the EnforcementReloaded this enforcer actor uses for doing command enforcements.
 */
public abstract class AbstractEnforcerActor<I extends EntityId, S extends Signal<?>, R extends CommandResponse<?>,
        E extends EnforcementReloaded<S, R>>
        extends AbstractActorWithStashWithTimers {

    /**
     * Timeout for local actor invocations - a small timeout should be more than sufficient as those are just method
     * calls.
     */
    protected static final Duration DEFAULT_LOCAL_ASK_TIMEOUT = Duration.ofSeconds(5);

    protected final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    protected final I entityId;
    protected final E enforcement;
    protected final PreEnforcerProvider preEnforcer;

    protected AbstractEnforcerActor(final I entityId, final E enforcement) {
        this.entityId = entityId;
        this.enforcement = enforcement;
        final var system = getContext().getSystem();
        final var dittoExtensionsConfig = ScopedConfig.dittoExtension(system.settings().config());
        preEnforcer = PreEnforcerProvider.get(system, dittoExtensionsConfig);
    }

    /**
     * Provides the {@link PolicyId} to use for the policy enforcement.
     * The implementation chooses the most efficient strategy to retrieve it.
     *
     * @return a successful CompletionStage of either the loaded {@link PolicyId} of the Policy which should be used
     * for enforcement or a failed CompletionStage with the cause for the failure.
     */
    protected abstract CompletionStage<PolicyId> providePolicyIdForEnforcement(final Signal<?> signal);

    /**
     * Provides the {@link PolicyEnforcer} instance (which holds a {@code Policy} + the built {@code Enforcer}) for the
     * provided {@code policyId} asynchronously.
     * The implementation chooses the most efficient strategy to retrieve it.
     *
     * @param policyId the {@link PolicyId} to retrieve the PolicyEnforcer for.
     * @return a successful CompletionStage of either an optional holding the loaded {@link PolicyEnforcer} or an empty optional if the enforcer could not be loaded.
     */
    protected abstract CompletionStage<Optional<PolicyEnforcer>> providePolicyEnforcer(@Nullable PolicyId policyId);

    @SuppressWarnings("unchecked")
    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(DistributedPubSubMediator.SubscribeAck.class, s -> log.debug("Got subscribeAck <{}>.", s))
                .match(SudoCommand.class, sudoCommand -> log.withCorrelationId(sudoCommand)
                        .error("Received SudoCommand in enforcer which should never happen: <{}>", sudoCommand)
                )
                .match(CommandResponse.class, r -> replyWithFilteredCommandResponse((R) r))
                .match(Signal.class, s -> enforceSignal((S) s))
                .matchAny(message ->
                        log.withCorrelationId(
                                        message instanceof WithDittoHeaders withDittoHeaders ? withDittoHeaders : null)
                                .warning("Got unknown message: '{}'", message))
                .build();
    }

    protected CompletionStage<Optional<PolicyEnforcer>> loadPolicyEnforcer(Signal<?> signal) {
        return providePolicyIdForEnforcement(signal)
                .thenCompose(this::providePolicyEnforcer);
    }

    /**
     * Enforces the passed {@code signal} using the {@code enforcement} of this actor.
     * Successfully enforced signals are sent back to the {@code getSender()} - which is our dear parent, the Supervisor.
     * Our parent is responsible for then forwarding the signal to the actual responsible target.
     *
     * @param signal the {@code Signal} to enforce based in the {@code policyEnforcer}.
     */
    private void enforceSignal(final S signal) {
        doEnforceSignal(signal, getSender());
    }

    @SuppressWarnings("unchecked")
    private void doEnforceSignal(final S signal, final ActorRef sender) {

        final var startedSpan = DittoTracing.newPreparedSpan(signal.getDittoHeaders(), SpanOperationName.of("enforce"))
                .start();
        final var tracedSignal =
                signal.setDittoHeaders(DittoHeaders.of(startedSpan.propagateContext(signal.getDittoHeaders())));
        final ActorRef self = getSelf();

        try {
            preEnforcer.apply(tracedSignal)
                    .thenApply(preEnforcedSignal -> (S) preEnforcedSignal)
                    .thenCompose(preEnforcedSignal -> {
                        startedSpan.mark("pre_enforced");
                        return loadPolicyEnforcer(preEnforcedSignal).thenCompose(optionalPolicyEnforcer -> {
                                    startedSpan.mark("enforcer_loaded");
                                    return optionalPolicyEnforcer
                                            .map(policyEnforcer -> enforcement.authorizeSignal(preEnforcedSignal,
                                                    policyEnforcer))
                                            .orElseGet(() -> enforcement.authorizeSignalWithMissingEnforcer(
                                                    preEnforcedSignal));
                                }
                        );
                    })
                    .whenComplete((authorizedSignal, throwable) -> {
                        if (null != authorizedSignal) {
                            startedSpan.mark("enforce_success").finish();
                            log.withCorrelationId(authorizedSignal)
                                    .info("Completed enforcement of message type <{}> with outcome 'success'",
                                            authorizedSignal.getType());
                            sender.tell(authorizedSignal, self);
                        } else if (null != throwable) {
                            startedSpan.mark("enforce_failed").tagAsFailed(throwable).finish();
                            handleAuthorizationFailure(tracedSignal, throwable, sender);
                        } else {
                            startedSpan.mark("enforce_error").tagAsFailed("unknown-outcome").finish();
                            log.withCorrelationId(tracedSignal)
                                    .warning("Neither authorizedSignal nor throwable were present during enforcement" +
                                                    " of signal: <{}>",
                                            tracedSignal);
                        }
                    });
        } catch (final DittoRuntimeException dittoRuntimeException) {
            startedSpan.mark("enforce_failed").tagAsFailed(dittoRuntimeException).finish();
            handleAuthorizationFailure(tracedSignal, dittoRuntimeException, sender);
        }
    }

    private void handleAuthorizationFailure(
            final Signal<?> signal,
            final Throwable throwable,
            final ActorRef sender
    ) {
        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        final DittoRuntimeException dittoRuntimeException =
                DittoRuntimeException.asDittoRuntimeException(throwable, t ->
                        DittoInternalErrorException.newBuilder()
                                .cause(t)
                                .dittoHeaders(dittoHeaders)
                                .build()
                );
        log.withCorrelationId(dittoRuntimeException)
                .info("Completed enforcement of message type <{}> with outcome 'failed' and headers: <{}>",
                        signal.getType(), dittoHeaders);
        sender.tell(dittoRuntimeException, getSelf());
    }

    /**
     * Filters the response payload of the passed {@code commandResponse} using the {@code enforcement} of this actor.
     * Filtered command responses are sent back to the {@code getSender()} - which is our dear parent, the Supervisor.
     * Our parent is responsible for then forwarding the command response to the original sender.
     *
     * @param commandResponse the {@code CommandResponse} to filter based in the {@code policyEnforcer}.
     */
    private void replyWithFilteredCommandResponse(final R commandResponse) {
        final ActorRef sender = getSender();
        final ActorRef parent = getContext().parent();
        if (enforcement.shouldFilterCommandResponse(commandResponse)) {
            Patterns.pipe(filterResponse(commandResponse), getContext().dispatcher()).to(sender, parent);
        } else {
            sender.tell(commandResponse, parent);
        }
    }

    /**
     * Filters the response payload of the passed {@code commandResponse} using the {@code enforcement} of this actor.
     *
     * @param commandResponse the {@code CommandResponse} to filter based in the {@code policyEnforcer}.
     * @return a completion stage holding the filtered command response.
     */
    private CompletionStage<R> filterResponse(final R commandResponse) {
        if (enforcement.shouldFilterCommandResponse(commandResponse)) {
            return providePolicyIdForEnforcement(commandResponse)
                    .thenCompose(id -> providePolicyEnforcer(id).thenApply(enforcer -> Pair.apply(id, enforcer)))
                    .thenApply(pair -> pair.second().orElseThrow(
                            () -> {
                                log.withCorrelationId(commandResponse)
                                        .debug("Could not filter command response because policyEnforcer was missing." +
                                                " Likely the policy was deleted during command processing.");
                                throw PolicyNotAccessibleException.newBuilder(pair.first()).build();
                            }))
                    .thenCompose(policyEnforcer -> doFilterResponse(commandResponse, policyEnforcer));
        } else {
            return CompletableFuture.completedStage(commandResponse);
        }
    }

    private CompletionStage<R> doFilterResponse(final R commandResponse, final PolicyEnforcer policyEnforcer) {
        try {
            final CompletionStage<R> filteredResponseStage =
                    enforcement.filterResponse(commandResponse, policyEnforcer);
            return filteredResponseStage.handle((filteredResponse, throwable) -> {
                if (null != filteredResponse) {
                    log.withCorrelationId(filteredResponse)
                            .info("Completed filtering of command response type <{}>",
                                    filteredResponse.getType());
                    return filteredResponse;
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
                    throw dittoRuntimeException;
                } else {
                    log.withCorrelationId(commandResponse)
                            .error("Neither filteredResponse nor throwable were present during filtering of " +
                                    "commandResponse: <{}>", commandResponse);
                    throw DittoInternalErrorException.newBuilder()
                            .dittoHeaders(commandResponse.getDittoHeaders())
                            .build();
                }
            });
        } catch (final DittoRuntimeException dittoRuntimeException) {
            log.withCorrelationId(dittoRuntimeException)
                    .info("Exception during filtering of command response type <{}> and headers: <{}>",
                            commandResponse.getType(), commandResponse.getDittoHeaders());
            throw dittoRuntimeException;
        }
    }

}
