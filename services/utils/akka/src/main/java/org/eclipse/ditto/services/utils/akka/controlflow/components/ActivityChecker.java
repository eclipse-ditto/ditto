/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.akka.controlflow.components;

import java.time.Duration;

import javax.annotation.Nullable;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.stream.FanInShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.SourceShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Source;

/**
 * Terminate a graph actor after a period of inactivity.
 */
public final class ActivityChecker {

    private ActivityChecker() {
        throw new AssertionError();
    }

    /**
     * Create an Akka stream graph to terminate an actor after a period of inactivity.
     *
     * @param interval how often to check for activity.
     * @param self reference to the actor.
     * @param <A> type of messages that prevents actor termination.
     * @return an activity checker.
     */
    public static <A> Graph<FlowShape<A, A>, NotUsed> of(final Duration interval, final ActorRef self) {
        return GraphDSL.create(builder -> {
            final SourceShape<Tick> ticker = builder.add(Source.tick(interval, interval, new Tick()));
            final FanInShape2<A, Tick, A> killer = builder.add(PipeWithIdleRoutine.of((tick, log) -> {
                log.debug("Terminating actor after <{}> of inactivity: <{}>", interval, self);
                self.tell(PoisonPill.getInstance(), ActorRef.noSender());
            }));
            builder.from(ticker).toInlet(killer.in1());
            return FlowShape.of(killer.in0(), killer.out());
        });
    }

    /**
     * Create an activity checker if duration is not null and a pipe otherwise.
     *
     * @param interval how often to check for activity.
     * @param self reference to the actor.
     * @param <A> type of messages that prevents actor termination.
     * @return an activity checker.
     */
    public static <A> Graph<FlowShape<A, A>, NotUsed> ofNullable(@Nullable final Duration interval,
            final ActorRef self) {

        return interval == null ? Flow.create() : of(interval, self);
    }

    private static final class Tick {}
}
