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
package org.eclipse.ditto.edge.service.dispatching;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.service.signaltransformer.SignalTransformer;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Signal transformer used for {@link EdgeCommandForwarderActorTest} to artificially delay certain commands in their
 * signal transformation.
 */
public final class EdgeCommandForwarderActorTestSignalTransformer implements SignalTransformer {

    private static final Duration CREATE_THING_TRANSFORMATION_DURATION = Duration.ofMillis(200);

    EdgeCommandForwarderActorTestSignalTransformer(final ActorSystem actorSystem, final Config config) {
    }

    @Override
    public CompletionStage<Signal<?>> apply(final Signal<?> signal) {
        if (signal instanceof CreateThing) {
            return new CompletableFuture<Signal<?>>()
                    .completeOnTimeout(signal, CREATE_THING_TRANSFORMATION_DURATION.toMillis(), TimeUnit.MILLISECONDS);
        } else {
            return CompletableFuture.completedStage(signal);
        }
    }

}
