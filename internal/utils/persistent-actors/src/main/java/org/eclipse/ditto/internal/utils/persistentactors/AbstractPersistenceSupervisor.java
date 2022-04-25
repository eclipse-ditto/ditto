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
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.api.commands.sudo.SudoCommand;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
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
import org.eclipse.ditto.policies.enforcement.CreationRestrictionEnforcer;
import org.eclipse.ditto.policies.enforcement.DefaultCreationRestrictionEnforcer;
import org.eclipse.ditto.policies.enforcement.config.DefaultEntityCreationConfig;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
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
 */
public abstract class AbstractPersistenceSupervisor<E extends EntityId> extends AbstractActorWithStashWithTimers {

    /**
     * Timeout for local actor invocations - a small timeout should be more than sufficient as those are just method
     * calls.
     */
    private static final Duration DEFAULT_LOCAL_ASK_TIMEOUT = Duration.ofSeconds(5);

    protected final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    protected final CreationRestrictionEnforcer creationRestrictionEnforcer;

    @Nullable private E entityId;
    @Nullable private ActorRef persistenceActorChild;

    @Nullable private ActorRef enforcerChild;

    private final ExponentialBackOffConfig exponentialBackOffConfig;
    private ExponentialBackOff backOff;
    private boolean waitingForStopBeforeRestart = false;

    protected AbstractPersistenceSupervisor() {
        exponentialBackOffConfig = getExponentialBackOffConfig();
        backOff = ExponentialBackOff.initial(exponentialBackOffConfig);
        creationRestrictionEnforcer = DefaultCreationRestrictionEnforcer.of(
                DefaultEntityCreationConfig.of(DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()))
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
                .matchEquals(Control.START_CHILDS, this::startChilds)
                .matchEquals(Control.PASSIVATE, this::passivate)
                .match(SudoCommand.class, this::forwardSudoCommandToChildIfAvailable)
                .matchAny(this::forwardToChildIfAvailable)
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
                    startChilds(Control.START_CHILDS);
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
                .match(ReceiveTimeout.class, timeout -> passivate(Control.PASSIVATE))
                .matchAny(this::replyUnavailableException)
                .build());
    }

    private void becomeActive(final ShutdownBehaviour shutdownBehaviour) {
        getContext().become(shutdownBehaviour.createReceive().build()
                .orElse(activeBehaviour()));
    }

    private void passivate(final Control passivationTrigger) {
        getContext().getParent().tell(new ShardRegion.Passivate(PoisonPill.getInstance()), getSelf());
    }

    private void startChilds(final Control startChild) {
        if (null == persistenceActorChild) {
            log.debug("Starting persistence actor for entity with ID <{}>.", entityId);
            assert entityId != null;
            final ActorRef paRef = getContext().actorOf(getPersistenceActorProps(entityId), "pa");
            persistenceActorChild = getContext().watch(paRef);
        } else {
            log.debug("Not starting persistence child actor because it is started already.");
        }

        startEnforcerActor();
    }

    private void startEnforcerActor() {
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
            if (waitingForStopBeforeRestart) {
                log.info("Persistence actor for entity with ID <{}> was stopped and will now be started again.",
                        entityId);
                persistenceActorChild = null;
                self().tell(Control.START_CHILDS, ActorRef.noSender());
            } else {
                if (message.getAddressTerminated()) {
                    log.error("Persistence actor for entity with ID <{}> terminated abnormally " +
                            "because it crashed or because of network failure!", entityId);
                } else {
                    log.warning("Persistence actor for entity with ID <{}> terminated abnormally.", entityId);
                }
                persistenceActorChild = null;
                backOff = backOff.calculateNextBackOff();
                final Duration restartDelay = backOff.getRestartDelay();
                getTimers().startSingleTimer(Control.START_CHILDS, Control.START_CHILDS, restartDelay);
            }
        } else if (message.getActor().equals(enforcerChild)) {
            // simply restart the enforcer actor
            startEnforcerActor();
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
                        .warning("Received unhandled SudoCommand from persistence child actor '{}': {}", entityId,
                                sudoCommand);
                unhandled(sudoCommand);
            } else {
                persistenceActorChild.forward(sudoCommand, getContext());
            }
        } else {
            replyUnavailableException(sudoCommand);
        }
    }

    /**
     * Forward all messages to the child if it is active or by reply immediately with an exception if the child has
     * terminated (fail fast).
     */
    private void forwardToChildIfAvailable(final Object message) {

        final ActorRef sender = getSender();
        if (message instanceof Command<?> command) {
            if (sender.equals(persistenceActorChild)) {
                log.withCorrelationId(command)
                        .warning("Received unhandled message from persistence child actor '{}': {}",
                                entityId, message);
                unhandled(message);
            } else if (sender.equals(enforcerChild)) {
                log.withCorrelationId(command)
                        .warning("Received unhandled message from persistence enforcer child actor '{}': {}",
                                entityId, message);
                unhandled(message);
            } else {
                // all commands must be enforced by the enforcer child, so ask all commands to it:
                enforceCommandAndForwardToPersistenceActorIfAuthorized(sender, command);
            }
        } else if (null != persistenceActorChild) {
            if (persistenceActorChild.equals(sender)) {
                log.withCorrelationId(message instanceof WithDittoHeaders withDittoHeaders ? withDittoHeaders : null)
                        .warning("Received unhandled message from child actor '{}': {}", entityId, message);
                unhandled(message);
            } else {
                persistenceActorChild.forward(message, getContext());
            }
        } else {
            replyUnavailableException(message);
        }
    }

    private void enforceCommandAndForwardToPersistenceActorIfAuthorized(final ActorRef sender,
            final Command<?> command) {

        if (null != enforcerChild) {
            Patterns.ask(enforcerChild, command, DEFAULT_LOCAL_ASK_TIMEOUT)
                    .whenComplete((enResponse, enThrowable) -> {
                        if (enResponse instanceof Command<?> enforcedCommand) {
                            Patterns.ask(persistenceActorChild, enforcedCommand, DEFAULT_LOCAL_ASK_TIMEOUT)
                                    .whenComplete((paResponse, paThrowable) -> {
                                        if (paResponse instanceof CommandResponse<?> commandResponse) {
                                            enforcerChild.tell(commandResponse, sender);
                                        } else if (enResponse instanceof DittoRuntimeException dre) {
                                            sender.tell(dre, persistenceActorChild);
                                        } else if (null != paThrowable) {
                                            sender.tell(paThrowable, persistenceActorChild);
                                        }
                                    });
                        } else if (enResponse instanceof DittoRuntimeException dre) {
                            sender.tell(dre, persistenceActorChild);
                        } else if (null != enThrowable) {
                            sender.tell(enThrowable, persistenceActorChild);
                        }
                    });
        } else {
            log.withCorrelationId(command)
                    .error("Could not enforce command because enforcer actor was not present");
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
                .info("Stashed received message during startup of supervised PersistenceActor: <{}>",
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
         * Request to start child actor.
         */
        START_CHILDS,

        /**
         * Signals initialization is done, child actors can be started.
         */
        INIT_DONE
    }

}
