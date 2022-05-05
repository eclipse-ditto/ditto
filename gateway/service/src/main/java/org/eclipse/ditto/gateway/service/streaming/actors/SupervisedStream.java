/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.streaming.actors;

import java.util.function.Consumer;

import akka.event.Logging;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;

/**
 * Materialized value of a source queue for supervision of the stream for which the queue is a part.
 * Provides a kill switch and a termination future.
 */
public interface SupervisedStream {

    /**
     * Create a source queue that materializes an additional value for supervision.
     *
     * @param queueSize size of the source queue.
     * @return the source queue.
     */
    static Source<SessionedJsonifiable, WithQueue> sourceQueue(final int queueSize) {
        return Source.<SessionedJsonifiable>queue(queueSize, OverflowStrategy.fail().withLogLevel(Logging.WarningLevel()))
                .viaMat(KillSwitches.single(), Keep.both())
                .mapMaterializedValue(pair -> {
                    final SourceQueueWithComplete<SessionedJsonifiable> sourceQueue = pair.first();
                    final KillSwitch killSwitch = pair.second();
                    final SupervisedStream supervised =
                            new DefaultSupervisedStream(killSwitch, sourceQueue.watchCompletion());
                    return new WithQueue(sourceQueue, supervised);
                });
    }

    /**
     * Add a listener for stream termination.
     *
     * @param errorConsumer called when the stream terminates. The argument is null after a normal termination
     * or an error after an abnormal termination.
     */
    void whenComplete(Consumer<? super Throwable> errorConsumer);

    /**
     * Shutdown the supervised stream.
     */
    void shutdown();

    /**
     * Abort the supervised stream with an error.
     *
     * @param error the error with which to fail the stream.
     */
    void abort(Throwable error);

    /**
     * Materialized value containing a {@code SourceQueue} and a {@code SupervisedSource}.
     */
    final class WithQueue {

        private final SourceQueueWithComplete<SessionedJsonifiable> sourceQueue;
        private final SupervisedStream supervisedStream;

        private WithQueue(final SourceQueueWithComplete<SessionedJsonifiable> sourceQueue,
                final SupervisedStream supervisedStream) {
            this.sourceQueue = sourceQueue;
            this.supervisedStream = supervisedStream;
        }

        /**
         * @return the source queue.
         */
        public SourceQueueWithComplete<SessionedJsonifiable> getSourceQueue() {
            return sourceQueue;
        }

        /**
         * @return the supervised source.
         */
        public SupervisedStream getSupervisedStream() {
            return supervisedStream;
        }
    }

}
