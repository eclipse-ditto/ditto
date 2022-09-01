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
package org.eclipse.ditto.thingsearch.service.common.util;

import java.net.ConnectException;
import java.util.NoSuchElementException;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;

import akka.actor.ActorKilledException;
import akka.actor.InvalidActorNameException;
import akka.actor.OneForOneStrategy;
import akka.actor.SupervisorStrategy;
import akka.event.LoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import akka.pattern.AskTimeoutException;

/**
 * Creates a {@link OneForOneStrategy} which can be used as Supervisor-Strategy for Root Actors, especially when they
 * have many different children.
 */
public final class RootSupervisorStrategyFactory {

    private static final String RESTARTING_CHILD_MSG = "Restarting child...";

    private RootSupervisorStrategyFactory() {
        throw new AssertionError();
    }

    public static OneForOneStrategy createStrategy(final LoggingAdapter log) {
        return new OneForOneStrategy(true, DeciderBuilder
                .match(NullPointerException.class, e -> {
                    log.error(e, "NullPointer in child actor: {}", e.getMessage());
                    log.info(RESTARTING_CHILD_MSG);
                    return SupervisorStrategy.restart();
                }).match(IllegalArgumentException.class, e -> {
                    log.warning("Illegal Argument in child actor: {}", e.getMessage());
                    return SupervisorStrategy.resume();
                }).match(IndexOutOfBoundsException.class, e -> {
                    log.warning("IndexOutOfBounds in child actor: {}", e.getMessage());
                    return SupervisorStrategy.resume();
                }).match(IllegalStateException.class, e -> {
                    log.warning("Illegal State in child actor: {}", e.getMessage());
                    return SupervisorStrategy.resume();
                }).match(NoSuchElementException.class, e -> {
                    log.warning("NoSuchElement in child actor: {}", e.getMessage());
                    return SupervisorStrategy.resume();
                }).match(AskTimeoutException.class, e -> {
                    log.warning("AskTimeoutException in child actor: {}", e.getMessage());
                    return SupervisorStrategy.resume();
                }).match(ConnectException.class, e -> {
                    log.warning("ConnectException in child actor: {}", e.getMessage());
                    log.info(RESTARTING_CHILD_MSG);
                    return SupervisorStrategy.restart();
                }).match(InvalidActorNameException.class, e -> {
                    log.warning("InvalidActorNameException in child actor: {}", e.getMessage());
                    return SupervisorStrategy.resume();
                }).match(ActorKilledException.class, e -> {
                    log.error(e, "ActorKilledException in child actor: {}", e.message());
                    log.info(RESTARTING_CHILD_MSG);
                    return SupervisorStrategy.restart();
                }).match(DittoRuntimeException.class, e -> {
                    log.error(e,
                            "DittoRuntimeException '{}' should not be escalated to RootActor. Simply resuming Actor.",
                            e.getErrorCode());
                    return SupervisorStrategy.resume();
                }).match(Throwable.class, e -> {
                    log.error(e, "Escalating above root actor!");
                    return SupervisorStrategy.escalate();
                }).matchAny(e -> {
                    log.error("Unknown message:'{}'! Escalating above root actor!", e);
                    return SupervisorStrategy.escalate();
                }).build());
    }

}
