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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.api.commands.sudo.SudoCommand;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.service.actors.ShutdownBehaviour;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOff;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.internal.utils.akka.actors.AbstractActorWithStashWithTimers;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.policies.enforcement.CreationRestrictionEnforcer;
import org.eclipse.ditto.policies.enforcement.DefaultCreationRestrictionEnforcer;
import org.eclipse.ditto.policies.enforcement.PreEnforcer;
import org.eclipse.ditto.policies.enforcement.config.DefaultEntityCreationConfig;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.SupervisorStrategy;
import akka.actor.Terminated;
import akka.cluster.sharding.ShardRegion;
import akka.japi.Pair;
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
 */
public abstract class AbstractPersistenceSupervisor<E extends EntityId> extends AbstractActorWithStashWithTimers {

    /**
     * Timeout for local actor invocations - a small timeout should be more than sufficient as those are just method
     * calls.
     */
    protected static final Duration DEFAULT_LOCAL_ASK_TIMEOUT = Duration.ofSeconds(5);

    protected final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    @Nullable protected final BlockedNamespaces blockedNamespaces;
    protected final PreEnforcer preEnforcer;
    protected final CreationRestrictionEnforcer creationRestrictionEnforcer;

    @Nullable protected E entityId;
    @Nullable protected ActorRef persistenceActorChild;

    @Nullable protected ActorRef enforcerChild;

    private final ExponentialBackOffConfig exponentialBackOffConfig;
    private ExponentialBackOff backOff;
    private boolean waitingForStopBeforeRestart = false;

    protected AbstractPersistenceSupervisor(@Nullable final BlockedNamespaces blockedNamespaces,
            final PreEnforcer preEnforcer) {
        this(null, null, blockedNamespaces, preEnforcer);
    }

    protected AbstractPersistenceSupervisor(@Nullable final ActorRef persistenceActorChild,
            @Nullable final ActorRef enforcerChild,
            @Nullable final BlockedNamespaces blockedNamespaces,
            final PreEnforcer preEnforcer) {

        this.persistenceActorChild = persistenceActorChild;
        this.enforcerChild = enforcerChild;
        this.blockedNamespaces = blockedNamespaces;
        this.preEnforcer = preEnforcer;
        exponentialBackOffConfig = getExponentialBackOffConfig();
        backOff = ExponentialBackOff.initial(exponentialBackOffConfig);
        creationRestrictionEnforcer = DefaultCreationRestrictionEnforcer.of(
                DefaultEntityCreationConfig.of(
                        DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
                )
        );
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
                .matchAny(this::enforceAndForwardToPersistenceActor)
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
    protected CompletionStage<Object> modifyEnforcerActorEnforcedCommandResponse(final Object enforcedCommand) {
        return CompletableFuture.completedStage(enforcedCommand);
    }

    /**
     * Hook for modifying a PersistenceActor command response before it gets sent to the EnforcerActor again for
     * filtering.
     *
     * @param enforcedCommand the already enforced command which was sent to the PersistenceActor.
     * @param persistenceCommandResponse the command response sent by the PersistenceActor to potentially modify.
     * @return the potentially modified command response.
     */
    protected CompletionStage<Object> modifyPersistenceActorCommandResponse(final Command<?> enforcedCommand,
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
                .matchAny(this::replyUnavailableException)
                .build());
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
        ensurePersistenceActorBeeingStarted();
        ensureEnforcerActorBeeingStarted();
    }

    private void ensurePersistenceActorBeeingStarted() {
        if (null == persistenceActorChild) {
            log.debug("Starting persistence actor for entity with ID <{}>.", entityId);
            assert entityId != null;
            final ActorRef paRef = getContext().actorOf(getPersistenceActorProps(entityId), "pa");
            persistenceActorChild = getContext().watch(paRef);
        } else {
            log.debug("Not starting persistence child actor because it is started already.");
        }
    }

    private void ensureEnforcerActorBeeingStarted() {
        if (null == enforcerChild) {
            log.debug("Starting enforcer actor for entity with ID <{}>.", entityId);
            assert entityId != null;
            final ActorRef enRef = getContext().actorOf(getPersistenceEnforcerProps(entityId), "en");
            enforcerChild = getContext().watch(enRef);
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
            ensureEnforcerActorBeeingStarted();
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
            replyUnavailableException(sudoCommand);
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
            replyUnavailableException(withDittoHeaders);
        }
    }

    /**
     * Forward all messages to the persistenceActorChild (after applied enforcement) if it is active or by reply
     * immediately with an exception if the child has terminated (fail fast).
     */
    private void enforceAndForwardToPersistenceActor(final Object message) {

        final ActorRef sender = getSender();
        if (message instanceof Command<?> command) {
            if (sender.equals(persistenceActorChild)) {
                log.withCorrelationId(command)
                        .warning("Received unhandled message from persistenceActorChild '{}': {}",
                                entityId, message);
                unhandled(message);
            } else if (sender.equals(enforcerChild)) {
                log.withCorrelationId(command)
                        .warning("Received unhandled message from enforcerChild '{}': {}",
                                entityId, message);
                unhandled(message);
            } else {
                enforceCommandAndForwardToPersistenceActor(command)
                        .whenComplete((response, throwable) -> {
                            if (null != throwable) {
                                if (!(throwable instanceof DittoRuntimeException)) {
                                    log.withCorrelationId(command)
                                            .warning("Encountered Throwable when interacting with enforcer or " +
                                                    "persistence child, telling sender: {}", throwable);
                                }

                                final DittoRuntimeException dre =
                                        DittoRuntimeException.asDittoRuntimeException(throwable, t ->
                                                DittoInternalErrorException.newBuilder()
                                                        .dittoHeaders(command.getDittoHeaders())
                                                        .cause(t)
                                                        .build());
                                log.withCorrelationId(dre)
                                        .info("Received DittoRuntimeException during enforcement or persistence, " +
                                                "telling sender: {}", dre);
                                sender.tell(dre, getSelf());
                            } else if (null != response) {
                                sender.tell(response, getSelf());
                            } else {
                                log.withCorrelationId(command)
                                        .error("Received nothing when enforcing command and forwarding to " +
                                                "persistence actor - this should not happen.");
                            }
                        });
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
            replyUnavailableException(message);
        }
    }

    /**
     * All commands must are treated in the following way:
     * <ul>
     * <li>they are sent to the enforcer child which enforces/applies authorization of the command</li>
     * <li>after successful enforcement, they are optionally modified in {@link #modifyEnforcerActorEnforcedCommandResponse(Object)}</li>
     * <li>afterwards, the enforced command is sent to the persistence actor child in {@link #enforcedCommandToPersistenceActor(org.eclipse.ditto.base.model.headers.DittoHeaders, Object)}</li>
     * <li>the persistence actor's response is handled in {@link #filterPersistenceActorResponseViaEnforcer(org.eclipse.ditto.base.model.signals.commands.Command, Object)}
     * where the enforcer applies optionally filtering of the response</li>
     * <li>the result is returned in the CompletionStage</li>
     * </ul>
     *
     * @param command the command to enforce and forward to the persistence actor
     * @return a successful CompletionStage with the command response or a failed stage with a DittoRuntimeException as
     * cause
     */
    private CompletionStage<Object> enforceCommandAndForwardToPersistenceActor(final Command<?> command) {

        if (null != enforcerChild) {
            return preEnforcer.apply(command).thenCompose(preEnforcedCommand ->
                    Patterns.ask(enforcerChild, preEnforcedCommand, DEFAULT_LOCAL_ASK_TIMEOUT)
                            .thenCompose(this::modifyEnforcerActorEnforcedCommandResponse)
                            .thenCompose(enforcedCommand -> enforcedCommandToPersistenceActor(
                                    preEnforcedCommand.getDittoHeaders(),
                                    enforcedCommand
                            ))
                            .thenCompose(persistenceActorResponse -> filterPersistenceActorResponseViaEnforcer(
                                    null != persistenceActorResponse ? persistenceActorResponse.first() : null,
                                    null != persistenceActorResponse ? persistenceActorResponse.second() : null
                            ))
                    );
        } else {
            log.withCorrelationId(command)
                    .error("Could not enforce command because enforcerChild was not present");
            return CompletableFuture.completedStage(null);
        }
    }

    private CompletionStage<Pair<Command<?>, Object>> enforcedCommandToPersistenceActor(
            final DittoHeaders dittoHeaders,
            @Nullable final Object enforcerResponse
    ) {
        if (null == persistenceActorChild) {
            throw getUnavailableExceptionBuilder(entityId)
                    .dittoHeaders(dittoHeaders)
                    .build();
        } else if (enforcerResponse instanceof Command<?> enforcedCommand) {
            log.withCorrelationId(enforcedCommand)
                    .debug("Received enforcedCommand from enforcerChild, forwarding to persistenceActorChild: {}",
                            enforcedCommand);
            return Patterns.ask(persistenceActorChild, enforcedCommand, DEFAULT_LOCAL_ASK_TIMEOUT)
                    .thenCompose(response -> modifyPersistenceActorCommandResponse(enforcedCommand, response))
                    .thenApply(response -> Pair.create(enforcedCommand, response));
        } else if (enforcerResponse instanceof DittoRuntimeException dre) {
            log.withCorrelationId(dittoHeaders)
                    .debug("Received DittoRuntimeException as response from enforcerChild: {}", dre);
            throw dre;
        } else {
            return CompletableFuture.completedStage(null);
        }
    }

    private CompletionStage<Object> filterPersistenceActorResponseViaEnforcer(
            @Nullable final Command<?> enforcedCommand,
            @Nullable final Object persistenceActorResponse
    ) {
        assert enforcerChild != null;
        if (persistenceActorResponse instanceof CommandResponse<?> commandResponse) {
            log.withCorrelationId(commandResponse)
                    .debug("Received CommandResponse from persistenceActorChild, " +
                            "telling enforcerChild to apply response filtering: {}", commandResponse);
            return Patterns.ask(enforcerChild, commandResponse, DEFAULT_LOCAL_ASK_TIMEOUT);
        } else if (persistenceActorResponse instanceof DittoRuntimeException dre) {
            log.withCorrelationId(enforcedCommand)
                    .debug("Received DittoRuntimeException as response from persistenceActorChild: {}", dre);
            throw dre;
        } else {
            log.withCorrelationId(enforcedCommand)
                    .warning("Unexpected response from persistenceActorChild: {}", persistenceActorResponse);
            return CompletableFuture.completedStage(null);
        }
    }

    private void replyUnavailableException(final Object message) {
        log.withCorrelationId(message instanceof WithDittoHeaders withDittoHeaders ? withDittoHeaders : null)
                .warning("Received message during downtime of child actor for Entity with ID <{}>: <{}>", entityId,
                        message);
        final DittoRuntimeExceptionBuilder<?> builder = getUnavailableExceptionBuilder(entityId);
        if (message instanceof WithDittoHeaders withDittoHeaders) {
            builder.dittoHeaders(withDittoHeaders.getDittoHeaders());
        }
        getSender().tell(builder.build(), getSelf());
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

}
