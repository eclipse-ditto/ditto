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
package org.eclipse.ditto.internal.utils.cluster;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ConnectException;
import java.util.NoSuchElementException;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.InvalidActorNameException;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import akka.pattern.AskTimeoutException;

/**
 * Supervisor actor for cluster singletons which accepts a {@link SupervisorStrategy} (e.g. the one from the root
 * actor).
 */
final class ClusterSingletonSupervisorActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final SupervisorStrategy supervisorStrategy;
    private final ActorRef child;

    @SuppressWarnings("unused")
    ClusterSingletonSupervisorActor(final Props childProps) {
        this.supervisorStrategy = buildDefaultSupervisorStrategy();
        this.child = getContext().actorOf(childProps, "supervised-child");
    }

    @SuppressWarnings("unused")
    ClusterSingletonSupervisorActor(final Props childProps, final SupervisorStrategy supervisorStrategy) {
        this.supervisorStrategy = supervisorStrategy;
        this.child = getContext().actorOf(childProps, "supervised-child");
    }

    private OneForOneStrategy buildDefaultSupervisorStrategy() {
        return new OneForOneStrategy(true, DeciderBuilder
                .match(NullPointerException.class, e -> {
                    log.error(e, "NullPointer in singleton actor: {}", e.getMessage());
                    return restartChild();
                }).match(IllegalArgumentException.class, e -> {
                    log.warning("Illegal Argument in singleton actor: {}", e.getMessage());

                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);

                    log.warning("Illegal Argument in singleton actor: {}", sw.toString());
                    return SupervisorStrategy.resume();
                }).match(IllegalStateException.class, e -> {
                    log.warning("Illegal State in singleton actor: {}", e.getMessage());
                    return SupervisorStrategy.resume();
                }).match(IndexOutOfBoundsException.class, e -> {
                    log.warning("IndexOutOfBounds in singleton actor: {}", e.getMessage());
                    return SupervisorStrategy.resume();
                }).match(NoSuchElementException.class, e -> {
                    log.warning("NoSuchElement in singleton actor: {}", e.getMessage());
                    return SupervisorStrategy.resume();
                }).match(AskTimeoutException.class, e -> {
                    log.warning("AskTimeoutException in singleton actor: {}", e.getMessage());
                    return SupervisorStrategy.resume();
                }).match(ConnectException.class, e -> {
                    log.warning("ConnectException in singleton actor: {}", e.getMessage());
                    return restartChild();
                }).match(InvalidActorNameException.class, e -> {
                    log.warning("InvalidActorNameException in singleton actor: {}", e.getMessage());
                    return SupervisorStrategy.resume();
                }).match(ActorKilledException.class, e -> {
                    log.error(e, "ActorKilledException in singleton actor: {}", e.message());
                    return restartChild();
                }).match(DittoRuntimeException.class, e -> {
                    log.error(e,
                            "DittoRuntimeException '{}' should not be escalated to SupervisorActor. Simply resuming Actor.",
                            e.getErrorCode());
                    return SupervisorStrategy.resume();
                }).match(UnsupportedOperationException.class, e -> {
                    log.error(e, "UnsupportedOperationException in singleton actor: {}",
                            e.getMessage());
                    terminateActorSystem();
                    return SupervisorStrategy.stop(); // only stopping as ActorSystem is terminated anyways
                }).match(Throwable.class, e -> {
                    log.error(e, "Escalating above root actor!");
                    terminateActorSystem();
                    return SupervisorStrategy.stop(); // only stopping as ActorSystem is terminated anyways
                }).matchAny(e -> {
                    log.error("Unknown message:'{}'! Escalating above root actor!", e);
                    terminateActorSystem();
                    return SupervisorStrategy.stop(); // only stopping as ActorSystem is terminated anyways
                }).build());
    }

    private SupervisorStrategy.Directive restartChild() {
        log.info("Restarting child ...");
        return SupervisorStrategy.restart();
    }

    private void terminateActorSystem() {
        log.error("Terminating ActorSystem as requested by ClusterSingletonSupervisorActor supervision strategy ...");
        getContext().getSystem().terminate();
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param childProps the Props of the child to create (the actual singleton).
     * @return the Akka configuration Props object.
     */
    public static Props props(final Props childProps) {
        return Props.create(ClusterSingletonSupervisorActor.class, childProps);
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param childProps the Props of the child to create (the actual singleton).
     * @param supervisorStrategy the {@link SupervisorStrategy} to apply.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Props childProps, final SupervisorStrategy supervisorStrategy) {

        return Props.create(ClusterSingletonSupervisorActor.class, childProps, supervisorStrategy);
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return supervisorStrategy;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().matchAny(msg -> child.forward(msg, getContext())).build();
    }
}
