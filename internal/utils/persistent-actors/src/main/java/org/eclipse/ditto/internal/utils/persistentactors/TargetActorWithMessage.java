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
package org.eclipse.ditto.internal.utils.persistentactors;

import java.time.Duration;
import java.util.function.Function;

import akka.actor.ActorRef;

/**
 * A supervisor internal message combining a {@code targetActor} as target for a contained {@code message} and
 * when configured with a non-zero {@code messageTimeout} applying a {@code Patterns.ask()} style.
 *
 * @param targetActor the target actor to ask/send the passed {@code message} to
 * @param message the message to pass/ask the {@code targetActor}
 * @param messageTimeout the duration to apply as message timeout - a {@link java.time.Duration#ZERO} will not use
 * "Patterns.ask", but will {@code tell} the passed {@code targetActor} the {@code message} instead.
 * @param responseOrErrorConverter a converter function which can convert the response or an occurred
 * {@link Throwable} to the actual response.
 */
public record TargetActorWithMessage(ActorRef targetActor,
                                     Object message,
                                     Duration messageTimeout,
                                     Function<Object, Object> responseOrErrorConverter) {}
