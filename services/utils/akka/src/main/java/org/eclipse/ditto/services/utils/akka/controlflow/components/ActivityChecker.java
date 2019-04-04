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
package org.eclipse.ditto.services.utils.akka.controlflow.components;

import java.time.Duration;
import java.util.Collections;
import java.util.function.Function;

import javax.annotation.Nullable;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.event.LoggingAdapter;
import akka.stream.FanInShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.SourceShape;
import akka.stream.UniformFanInShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Merge;
import akka.stream.javadsl.Source;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

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
     * Create an activity checker. The actor to terminate is obtained from the first message.
     *
     * @param interval how often to check for activity.
     * @param getSelf function to extract the actor reference from a stream element.
     * @param getLog function to extract the log from a stream element.
     * @param <A> type of messages that prevents actor termination.
     * @return an activity checker.
     */
    public static <A> Graph<FlowShape<A, A>, NotUsed> of(final Duration interval, final Function<A, ActorRef> getSelf,
            final Function<A, LoggingAdapter> getLog) {

        return GraphDSL.create(builder -> {
            final FlowShape<A, Either<A, Tick>> receiver = builder.add(Flow.fromFunction(Left::apply));
            final SourceShape<Either<A, Tick>> ticker =
                    builder.add(Source.tick(interval, interval, new Tick()).map(Right::apply));
            final UniformFanInShape<Either<A, Tick>, Either<A, Tick>> merger = builder.add(Merge.create(2));
            final FlowShape<Either<A, Tick>, A> killer =
                    builder.add(Flow.<Either<A, Tick>>create()
                            .statefulMapConcat(() -> new StatefulConcatMapper<>(getSelf, getLog)));

            builder.from(receiver.out()).toInlet(merger.in(0));
            builder.from(ticker.out()).toInlet(merger.in(1));
            builder.from(merger.out()).toInlet(killer.in());

            return FlowShape.of(receiver.in(), killer.out());
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

    /**
     * Create an activity checker if duration is not null and a pipe otherwise.
     *
     * @param interval how often to check for activity.
     * @param getSelf function to extract the actor reference from a stream element.
     * @param getLog function to extract the log from a stream element.
     * @param <A> type of messages that prevents actor termination.
     * @return an activity checker.
     */
    public static <A> Graph<FlowShape<A, A>, NotUsed> ofNullable(@Nullable final Duration interval,
            final Function<A, ActorRef> getSelf,
            final Function<A, LoggingAdapter> getLog) {

        return interval == null ? Flow.create() : of(interval, getSelf, getLog);
    }

    private static final class Tick {}

    private static final class StatefulConcatMapper<A>
            implements akka.japi.function.Function<Either<A, Tick>, Iterable<A>> {

        private final Function<A, ActorRef> getSelf;
        private final Function<A, LoggingAdapter> getLog;
        private ActorRef self;
        private LoggingAdapter log;
        private int accessCounter = 0;
        private int accessCounterByLastTick = 0;

        private StatefulConcatMapper(final Function<A, ActorRef> getSelf, final Function<A, LoggingAdapter> getLog) {
            this.getSelf = getSelf;
            this.getLog = getLog;
        }

        @Override
        public Iterable<A> apply(final Either<A, Tick> input) {
            if (input.isLeft()) {
                final A message = input.left().get();
                if (accessCounter == 0) {
                    self = getSelf.apply(message);
                    log = getLog.apply(message);
                }
                ++accessCounter;
                return Collections.singletonList(message);
            } else {
                if (accessCounter == accessCounterByLastTick && self != null) {
                    // no message since last access; kill actor.
                    if (log != null) {
                        log.debug("Terminating actor due to inactivity: <{}>", self);
                    }
                    self.tell(PoisonPill.getInstance(), ActorRef.noSender());
                }
                accessCounterByLastTick = accessCounter;
                return Collections.emptyList();
            }
        }
    }
}
