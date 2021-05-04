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

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import akka.Done;
import akka.stream.KillSwitch;

/**
 * Implementation of {@link SupervisedStream}.
 */
final class DefaultSupervisedStream implements SupervisedStream {

    private final KillSwitch killSwitch;
    private final CompletionStage<Done> completionFuture;

    DefaultSupervisedStream(final KillSwitch killSwitch, final CompletionStage<Done> completionFuture) {
        this.killSwitch = killSwitch;
        this.completionFuture = completionFuture;
    }

    @Override
    public void whenComplete(final Consumer<? super Throwable> errorConsumer) {
        completionFuture.whenComplete((done, error) -> errorConsumer.accept(error));
    }

    @Override
    public void shutdown() {
        killSwitch.shutdown();
    }

    @Override
    public void abort(final Throwable error) {
        killSwitch.abort(error);
    }

}
