/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import java.text.MessageFormat;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.api.commands.sudo.SudoCommand;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.service.actors.ShutdownBehaviour;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOff;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.internal.utils.akka.actors.AbstractActorWithStashWithTimers;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.policies.enforcement.pre.PreEnforcerProvider;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.actor.Terminated;
import akka.cluster.sharding.ShardRegion;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;

/**
 * Sharded Supervisor of persistent actors. It:
 * <ol>
 * <li>restarts failed child actor after exponential backoff,</li>
 * <li>shuts down self on command, and</li>
 * <li>handles initialization errors by becoming corrupted for a time.</li>
 * </ol>
 *
 * @param <E> the type of the EntityId
 * @param <S> the type of the Signal
 */
public abstract class AbstractPersistenceSupervisor<E extends EntityId, S extends Signal<?>>
        extends AbstractActorWithStashWithTimers {

    /**
     * Timeout for local actor invocations - a small timeout should be more than sufficient as those are just method
     * calls.
     */
    protected static final Duration DEFAULT_LOCAL_ASK_TIMEOUT = Duration.ofSeconds(5);

    protected static final String PERSISTENCE_ACTOR_NAME = "pa";

    protected final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    @Nullable protected final BlockedNamespaces blockedNamespaces;
    @Nullable protected E entityId;
    @Nullable protected ActorRef persistenceActorChild;

    @Nullable protected ActorRef enforcerChild;

    private final PreEnforcerProvider preEnforcer;
    private final ExponentialBackOffConfig exponentialBackOffConfig;
    private ExponentialBackOff backOff;
    private boolean waitingForStopBeforeRestart = false;

    protected AbstractPersistenceSupervisor(@Nullable final BlockedNamespaces blockedNamespaces) {
        this(null, null, blockedNamespaces);
    }

    protected AbstractPersistenceSupervisor(@Nullable final ActorRef persistenceActorChild,
            @Nullable final ActorRef enforcerChild,
            @Nullable final BlockedNamespaces blockedNamespaces) {

        this.persistenceActorChild = persistenceActorChild;
        this.enforcerChild = enforcerChild;
        this.blockedNamespaces = blockedNamespaces;
        preEnforcer = PreEnforcerProvider.get(getContext().getSystem());
        exponentialBackOffConfig = getExponentialBackOffConfig();
        backOff = ExponentialBackOff.initial(exponentialBackOffConfig);
    }

    /**
     * @return ID of the entity this actor supervises.
     * @throws Exception if entity ID of this actor cannot be determined.
     */
    protected abstract E getEntityId() throws Exception;

    /**
     * Get the props of the supervised persistence actor.
     *
     * @param entityId entity ID of this actor.
     * @return props of the child actor.
     */
    protected abstract Props getPersistenceActorProps(E entityId);

    /**
     * Get the props of the supervised persistence enforcer actor.
     *
     * @param entityId entity ID of this actor.
     * @return props of the child actor.
     */
    protected abstract Props getPersistenceEnforcerProps(E entityId);

    /**
     * Read background configuration from actor context.
     * Called in constructor.
     * DO NOT rely on instance fields as they will not be initialized.
     *
     * @return exponential backoff configuration read from the actor system's settings.
     */
    protected abstract ExponentialBackOffConfig getExponentialBackOffConfig();

    /**
     * Get the shutdown behavior appropriate for this actor.
     *
     * @param entityId ID of the entity this actor supervises.
     * @return the shutdown behavior.
     */
    protected abstract ShutdownBehaviour getShutdownBehaviour(E entityId);

    /**
     * Whether to start child actor immediately in {@link #preStart()} method or wait for {@link Control#INIT_DONE}
     * message to start supervised child actor.
     *
     * @return {@code true} if child actor is started in {@link #preStart()} method or {@code false} if the
     * implementation signals finished initialization with {@link Control#INIT_DONE} message. Default is {@code true}.
     */
    protected boolean isStartChildImmediately() {
        return true;
    }

    protected Receive activeBehaviour() {
        return ReceiveBuilder.create()
                .match(Terminated.class, this::childTerminated)
                .matchEquals(Control.START_CHILDREN, this::startChildren)
                .matchEquals(Control.PASSIVATE, this::passivate)
                .match(SudoCommand.class, this::forwardSudoCommandToChildIfAvailable)
                .match(WithDittoHeaders.class, w -> w.getDittoHeaders().isSudo(),
                        this::forwardDittoSudoToChildIfAvailable)
                .matchAny(this::enforceAndForwardToTargetActor)
                .build();
    }

    /**
     * Create a builder for an exception to report unavailability of the entity.
     *
     * @param entityId the entity ID, or null if the actor is corrupted.
     * @return the exception builder.
     */
    protected abstract DittoRuntimeExceptionBuilder<?> getUnavailableExceptionBuilder(@Nullable E entityId);

    /**
     * Hook for modifying an EnforcerActor enforced command before it gets sent to the PersistenceActor.
     *
     * @param enforcedCommand the already enforced command to potentially modify.
     * @return the potentially modified command.
     */
    protected CompletionStage<Object> modifyEnforcerActorEnforcedSignalResponse(final Object enforcedCommand) {
        return CompletableFuture.completedStage(enforcedCommand);
    }

    /**
     * Hook for modifying a PersistenceActor command response before it gets sent to the EnforcerActor again for
     * filtering.
     *
     * @param enforcedSignal the already enforced signal which was sent to the PersistenceActor.
     * @param persistenceCommandResponse the command response sent by the PersistenceActor to potentially modify.
     * @return the potentially modified command response.
     */
    protected CompletionStage<Object> modifyTargetActorCommandResponse(final Signal<?> enforcedSignal,
            final Object persistenceCommandResponse) {
        return CompletableFuture.completedStage(persistenceCommandResponse);
    }

    /**
     * Return a preferably static supervisor strategy for this actor. By default, child actor is stopped when killed
     * or failing, triggering restart after exponential back-off.
     * Overriding method should return a static object if possible to conserve memory.
     *
     * @return The default supervisor strategy.
     */
    @Override
    public SupervisorStrategy supervisorStrategy() {
        return SupervisorStrategy.stoppingStrategy();
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        try {
            entityId = getEntityId();
            if (isStartChildImmediately()) {
                getSelf().tell(Control.INIT_DONE, getSelf());
            } else {
                log.debug("Not starting child actor, waiting for initialization to be finished.");
            }
        } catch (final Exception e) {
            log.error(e, "Failed to determine entity ID; becoming corrupted.");
            becomeCorrupted();
        }
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchEquals(Control.INIT_DONE, initDone -> {
                    entityId = getEntityId();
                    startChildren(Control.START_CHILDREN);
                    unstashAll();
                    becomeActive(getShutdownBehaviour(entityId));
                })
                .matchAny(this::handleMessagesDuringStartup)
                .build();
    }

    /**
     * Become corrupted because this actor cannot start.
     */
    protected void becomeCorrupted() {
        getContext().setReceiveTimeout(getCorruptedReceiveTimeout());
        getContext().become(ReceiveBuilder.create()
                .match(ReceiveTimeout.class, timeout -> {
                    getContext().cancelReceiveTimeout();
                    passivate(Control.PASSIVATE);
                })
                .matchAny(message -> replyUnavailableException(message, getSender()))
                .build());
    }

    /**
     * Asks the {@link org.eclipse.ditto.policies.enforcement.AbstractEnforcerActor} child the passed {@code signal},
     * responding with a CompletionStage which is completed with the response.
     * As the enforcer child is always located on the same node, this is a local method call, so the applied ask timeout
     * is very low.
     *
     * @param signal the signal to ask the enforcer.
     * @return the completion stage with the response (the enforced signal) or a failed stage when e.g. enforcement
     * failed due to lacking permissions.
     */
    protected CompletionStage<Object> askEnforcerChild(final Signal<?> signal) {
        return Patterns.ask(enforcerChild, signal, DEFAULT_LOCAL_ASK_TIMEOUT);
    }

    /**
     * Asks the "target actor" (being either the {@link AbstractPersistenceActor} for "twin" commands or e.g. a
     * pub/sub actor reference for "live" commands/messages) - which is determined by
     * {@link #getTargetActorForSendingEnforcedMessageTo(Object, akka.actor.ActorRef)} - the passed {@code message}.
     *
     * @param message the message to ask the target actor.
     * @param sender the sender which originally sent the message.
     * @param <T> the type of the message.
     * @return the completion stage with the response for the message or a failed stage.
     */
    protected <T> CompletionStage<Object> askTargetActor(final T message, final ActorRef sender) {
        return getTargetActorForSendingEnforcedMessageTo(message, sender)
                .thenCompose(this::askOrForwardToTargetActor)
                .thenApply(response -> {
                    if (null == response) {
                        throw getUnavailableExceptionBuilder(entityId)
                                .dittoHeaders(getDittoHeaders(message))
                                .build();
                    } else {
                        return response;
                    }
                });
    }

    private CompletionStage<Object> askOrForwardToTargetActor(
            @Nullable final TargetActorWithMessage targetActorWithMessage) {
        if (null != targetActorWithMessage) {
            if (!targetActorWithMessage.messageTimeout().isZero()) {
                return Patterns.ask(
                                targetActorWithMessage.targetActor(),
                                targetActorWithMessage.message(),
                                targetActorWithMessage.messageTimeout()
                        )
                        .thenApply(targetActorWithMessage.responseOrErrorConverter())
                        .exceptionally(throwable -> targetActorWithMessage.responseOrErrorConverter().apply(throwable));
            } else {
                targetActorWithMessage.targetActor().tell(targetActorWithMessage.message(), getSelf());
                return CompletableFuture.completedStage(new Status.Success(MessageFormat.format(
                        "message <{0}> sent via tell", targetActorWithMessage.message().getClass().getSimpleName()))
                );
            }
        } else {
            return CompletableFuture.completedStage(null);
        }
    }

    /**
     * Determines the {@link TargetActorWithMessage} for sending the passed enforced {@code message} to.
     * May be overwritten by implementations to determine the target actor in a different way.
     *
     * @param message the message to determine the target actor for.
     * @param sender the sender which originally sent the message.
     * @param <T> the type of the message.
     * @return the completion stage with the determined {@link TargetActorWithMessage} which includes the target actor
     * and the message to send it to
     */
    protected <T> CompletionStage<TargetActorWithMessage> getTargetActorForSendingEnforcedMessageTo(final T message,
            final ActorRef sender) {
        if (null != persistenceActorChild) {
            return CompletableFuture.completedStage(
                    new TargetActorWithMessage(
                            persistenceActorChild,
                            message,
                            DEFAULT_LOCAL_ASK_TIMEOUT,
                            Function.identity()
                    ));
        } else {
            return CompletableFuture.completedStage(null);
        }
    }

    private static DittoHeaders getDittoHeaders(final Object message) {
        return message instanceof WithDittoHeaders wdh ? wdh.getDittoHeaders() : DittoHeaders.empty();
    }

    private void becomeActive(final ShutdownBehaviour shutdownBehaviour) {
        getContext().become(shutdownBehaviour.createReceive().build()
                .orElse(activeBehaviour()));
    }

    private void passivate(final Control passivationTrigger) {
        getContext().cancelReceiveTimeout();
        getContext().getParent().tell(new ShardRegion.Passivate(PoisonPill.getInstance()), getSelf());
    }

    private void startChildren(final Control startChild) {
        ensurePersistenceActorBeingStarted();
        ensureEnforcerActorBeingStarted();
    }

    private void ensurePersistenceActorBeingStarted() {
        if (null == persistenceActorChild) {
            log.debug("Starting persistence actor for entity with ID <{}>.", entityId);
            assert entityId != null;
            final ActorRef paRef = getContext().actorOf(getPersistenceActorProps(entityId), PERSISTENCE_ACTOR_NAME);
            persistenceActorChild = getContext().watch(paRef);
        } else {
            log.debug("Not starting persistence child actor because it is started already.");
        }
    }

    private void ensureEnforcerActorBeingStarted() {
        if (null == enforcerChild) {
            log.debug("Starting enforcer actor for entity with ID <{}>.", entityId);
            assert entityId != null;
            final ActorRef enRef = getContext().actorOf(getPersistenceEnforcerProps(entityId), "en");
            enforcerChild = getContext().watch(enRef);
            log.debug("Now watching enforcer child: <{}>", enRef);
        } else {
            log.debug("Not starting persistence enforcer child actor because it is started already.");
        }
    }

    protected void restartChild() {
        if (persistenceActorChild != null) {
            waitingForStopBeforeRestart = true;
            getContext().stop(persistenceActorChild); // start happens when "Terminated" message is received.
        }
    }

    private void childTerminated(final Terminated message) {
        if (message.getActor().equals(persistenceActorChild)) {
            persistenceActorChild = null;
            if (waitingForStopBeforeRestart) {
                log.info("Persistence actor for entity with ID <{}> was stopped and will now be started again.",
                        entityId);
                self().tell(Control.START_CHILDREN, ActorRef.noSender());
            } else {
                if (message.getAddressTerminated()) {
                    log.error("Persistence actor for entity with ID <{}> terminated abnormally " +
                            "because it crashed or because of network failure!", entityId);
                } else {
                    log.warning("Persistence actor for entity with ID <{}> terminated abnormally.", entityId);
                }
                backOff = backOff.calculateNextBackOff();
                final Duration restartDelay = backOff.getRestartDelay();
                getTimers().startSingleTimer(Control.START_CHILDREN, Control.START_CHILDREN, restartDelay);
            }
        } else if (message.getActor().equals(enforcerChild)) {
            enforcerChild = null;
            // simply restart the enforcer actor
            ensureEnforcerActorBeingStarted();
        }
    }

    private Duration getCorruptedReceiveTimeout() {
        return randomize(exponentialBackOffConfig.getCorruptedReceiveTimeout(),
                exponentialBackOffConfig.getRandomFactor());
    }

    /**
     * Return a random duration between the base duration and {@code (1 + randomFactor)} times the base duration.
     *
     * @param base the base duration.
     * @param randomFactor the random factor.
     * @return the random duration.
     */
    private static Duration randomize(final Duration base, final double randomFactor) {
        final double multiplier = 1.0 + ThreadLocalRandom.current().nextDouble() * randomFactor;
        return Duration.ofMillis((long) (base.toMillis() * multiplier));
    }

    /**
     * Forward all SudoCommand directly (bypassing enforcer) to the child if it is active or by reply immediately with
     * an exception if the child has terminated (fail fast).
     */
    private void forwardSudoCommandToChildIfAvailable(final SudoCommand<?> sudoCommand) {
        if (null != persistenceActorChild) {
            if (persistenceActorChild.equals(getSender())) {
                log.withCorrelationId(sudoCommand)
                        .warning("Received unhandled SudoCommand from persistenceActorChild '{}': {}", entityId,
                                sudoCommand);
                unhandled(sudoCommand);
            } else {
                persistenceActorChild.forward(sudoCommand, getContext());
            }
        } else {
            replyUnavailableException(sudoCommand, getSender());
        }
    }

    private void forwardDittoSudoToChildIfAvailable(final WithDittoHeaders withDittoHeaders) {
        if (null != persistenceActorChild) {
            if (persistenceActorChild.equals(getSender())) {
                log.withCorrelationId(withDittoHeaders)
                        .warning("Received unhandled WithDittoHeaders from persistenceActorChild '{}': {}", entityId,
                                withDittoHeaders);
                unhandled(withDittoHeaders);
            } else {
                persistenceActorChild.forward(withDittoHeaders, getContext());
            }
        } else {
            replyUnavailableException(withDittoHeaders, getSender());
        }
    }

    /**
     * Forward all messages to the persistenceActorChild (after applied enforcement) if it is active or by reply
     * immediately with an exception if the child has terminated (fail fast).
     */
    private void enforceAndForwardToTargetActor(final Object message) {

        final ActorRef sender = getSender();
        if (message instanceof Signal<?> signal) {
            if (sender.equals(persistenceActorChild)) {
                log.withCorrelationId(signal)
                        .warning("Received unhandled message from persistenceActorChild '{}': {}",
                                entityId, message);
                unhandled(message);
            } else if (sender.equals(enforcerChild)) {
                log.withCorrelationId(signal)
                        .warning("Received unhandled message from enforcerChild '{}': {}",
                                entityId, message);
                unhandled(message);
            } else {
                enforceSignalAndForwardToTargetActor((S) signal, sender)
                        .whenComplete(handleSignalEnforcementResponse(signal, sender));
            }
        } else if (null != persistenceActorChild) {
            if (persistenceActorChild.equals(sender)) {
                log.withCorrelationId(message instanceof WithDittoHeaders withDittoHeaders ? withDittoHeaders : null)
                        .warning("Received unhandled message from persistenceActorChild '{}': {}", entityId, message);
                unhandled(message);
            } else {
                persistenceActorChild.forward(message, getContext());
            }
        } else {
            replyUnavailableException(message, sender);
        }
    }

    private BiConsumer<Object, Throwable> handleSignalEnforcementResponse(final WithDittoHeaders signal,
            final ActorRef sender) {

        return (response, throwable) -> {
            if (null != throwable) {
                final DittoRuntimeException dre =
                        DittoRuntimeException.asDittoRuntimeException(throwable, t -> {
                            log.withCorrelationId(signal)
                                    .warning("Encountered Throwable when interacting with enforcer " +
                                            "or target actor, telling sender: {}", throwable);
                            return DittoInternalErrorException.newBuilder()
                                    .dittoHeaders(signal.getDittoHeaders())
                                    .cause(t)
                                    .build();
                        });
                log.withCorrelationId(dre)
                        .info("Received DittoRuntimeException during enforcement or " +
                                "forwarding to target actor, telling sender: {}", dre);
                sender.tell(dre, getSelf());
            } else if (response instanceof Status.Success success) {
                log.debug("Ignoring Status.Success message as expected 'to be ignored' outcome: <{}>", success);
            } else if (null != response) {
                sender.tell(response, getSelf());
            } else {
                log.withCorrelationId(signal)
                        .error("Received nothing when enforcing signal and forwarding to " +
                                "target actor - this should not happen.");
                replyUnavailableException(signal, sender);
            }
        };
    }

    /**
     * All signals are treated in the following way:
     * <ul>
     * <li>they are sent to the enforcer child which enforces/applies authorization of the signal</li>
     * <li>after successful enforcement, they are optionally modified in {@link #modifyEnforcerActorEnforcedSignalResponse(Object)}</li>
     * <li>afterwards, the enforced signal is sent to the persistence actor child in
     * {@link #enforcerResponseToTargetActor(org.eclipse.ditto.base.model.headers.DittoHeaders, Object, akka.actor.ActorRef)}</li>
     * <li>the persistence actor's response is handled in
     * {@link #filterTargetActorResponseViaEnforcer(org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceSupervisor.EnforcedSignalAndTargetActorResponse)}
     * where the enforcer applies optionally filtering of the response</li>
     * <li>the result is returned in the CompletionStage</li>
     * </ul>
     *
     * @param signal the signal to enforce and forward to the target actor
     * @param sender the original sender of the signal.
     * @return a successful CompletionStage with the signal response or a failed stage with a DittoRuntimeException as
     * cause
     */
    protected CompletionStage<Object> enforceSignalAndForwardToTargetActor(final S signal, final ActorRef sender) {

        if (null != enforcerChild) {
            return preEnforcer.apply(signal).thenCompose(preEnforcedSignal ->
                    askEnforcerChild(preEnforcedSignal)
                            .thenCompose(this::modifyEnforcerActorEnforcedSignalResponse)
                            .thenCompose(enforcedCommand -> enforcerResponseToTargetActor(
                                    preEnforcedSignal.getDittoHeaders(),
                                    enforcedCommand,
                                    sender
                            ))
                            .thenCompose(this::filterTargetActorResponseViaEnforcer)
            );
        } else {
            log.withCorrelationId(signal)
                    .error("Could not enforce signal because enforcerChild was not present");
            return CompletableFuture.completedStage(null);
        }
    }

    private CompletionStage<EnforcedSignalAndTargetActorResponse> enforcerResponseToTargetActor(
            final DittoHeaders dittoHeaders,
            @Nullable final Object enforcerResponse,
            final ActorRef sender
    ) {
        if (null == persistenceActorChild) {
            throw getUnavailableExceptionBuilder(entityId)
                    .dittoHeaders(dittoHeaders)
                    .build();
        } else if (enforcerResponse instanceof Signal<?> enforcedSignal) {
            log.withCorrelationId(enforcedSignal)
                    .debug("Received enforcedSignal from enforcerChild, forwarding to target actor: {}",
                            enforcedSignal);
            return askTargetActor(enforcedSignal, sender)
                    .thenCompose(response ->
                            modifyTargetActorCommandResponse(enforcedSignal, response))
                    .thenApply(response ->
                            new EnforcedSignalAndTargetActorResponse(enforcedSignal, response)
                    );
        } else if (enforcerResponse instanceof DistributedPubWithMessage distributedPubWithMessage) {
            return askTargetActor(distributedPubWithMessage, sender)
                    .thenCompose(response ->
                            modifyTargetActorCommandResponse(distributedPubWithMessage.signal(), response))
                    .thenApply(response ->
                            new EnforcedSignalAndTargetActorResponse(distributedPubWithMessage.signal(), response)
                    );
        } else if (enforcerResponse instanceof DittoRuntimeException dre) {
            log.withCorrelationId(dittoHeaders)
                    .debug("Received DittoRuntimeException as response from enforcerChild: {}", dre);
            throw dre;
        } else {
            return CompletableFuture.completedStage(new EnforcedSignalAndTargetActorResponse(null, null));
        }
    }

    protected CompletionStage<Object> filterTargetActorResponseViaEnforcer(
            final EnforcedSignalAndTargetActorResponse targetActorResponse) {

        if (targetActorResponse.response() instanceof CommandResponse<?> commandResponse) {
            log.withCorrelationId(commandResponse)
                    .debug("Received CommandResponse from target actor, " +
                            "telling enforcerChild to apply response filtering: {}", commandResponse);
            return askEnforcerChild(commandResponse);
        } else if (targetActorResponse.response() instanceof DittoRuntimeException dre) {
            log.withCorrelationId(targetActorResponse.enforcedSignal())
                    .debug("Received DittoRuntimeException as response from target actor: {}", dre);
            throw dre;
        } else if (targetActorResponse.response() instanceof Status.Success success) {
            log.withCorrelationId(targetActorResponse.enforcedSignal())
                    .info("Got success message from target actor: {}", success);
            return CompletableFuture.completedStage(success);
        } else {
            log.withCorrelationId(targetActorResponse.enforcedSignal())
                    .warning("Unexpected response from target actor: {}", targetActorResponse);
            return CompletableFuture.completedStage(null);
        }
    }

    private void replyUnavailableException(final Object message, final ActorRef sender) {
        log.withCorrelationId(message instanceof WithDittoHeaders withDittoHeaders ? withDittoHeaders : null)
                .warning("Received message during downtime of child actor for Entity with ID <{}>: <{}>", entityId,
                        message);
        final DittoRuntimeExceptionBuilder<?> builder = getUnavailableExceptionBuilder(entityId);
        if (message instanceof WithDittoHeaders withDittoHeaders) {
            builder.dittoHeaders(withDittoHeaders.getDittoHeaders());
        }
        sender.tell(builder.build(), getSelf());
    }

    private void handleMessagesDuringStartup(final Object message) {
        stash();
        log.withCorrelationId(message instanceof WithDittoHeaders withDittoHeaders ? withDittoHeaders : null)
                .debug("Stashed received message during startup of supervised PersistenceActor: <{}>",
                        message.getClass().getSimpleName());
    }

    /**
     * Control message for the supervisor actor.
     */
    public enum Control {
        /**
         * Request for graceful shutdown.
         */
        PASSIVATE,

        /**
         * Request to start child actors.
         */
        START_CHILDREN,

        /**
         * Signals initialization is done, child actors can be started.
         */
        INIT_DONE
    }

    private record EnforcedSignalAndTargetActorResponse(@Nullable Signal<?> enforcedSignal,
                                                        @Nullable Object response) {}
}
