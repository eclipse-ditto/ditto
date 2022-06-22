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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.concurrent.NotThreadSafe;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.Props;
import akka.event.LoggingAdapter;

/**
 * This class takes care to start and to stop child actors in the context of a particular {@link ActorRefFactory}.
 * Main distinguishing functionality is to start child actors conflict free
 * (see {@link #startChildActorConflictFree(CharSequence, Props)}).
 * In this case the names of the actors to be started are concatenated with a count to make the names unique.
 */
@NotThreadSafe // Cannot be thread-safe because of ActorRefFactory.
public final class ChildActorNanny {

    private final ActorRefFactory actorRefFactory;
    private final LoggingAdapter logger;
    private final Map<String, AtomicInteger> childActorCounts;

    private ChildActorNanny(final ActorRefFactory actorRefFactory, final LoggingAdapter logger) {
        this.actorRefFactory = actorRefFactory;
        this.logger = logger;
        childActorCounts = new HashMap<>();
    }

    /**
     * Returns a new instance of {@code ChildActorNanny} for the specified {@code ActorRefFactory} and
     * {@code LoggingAdapter} arguments.
     *
     * @param actorRefFactory creates child actor {@code ActorRef}s and stops child actors by ActorRefs.
     * @param logger used for logging.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ChildActorNanny newInstance(final ActorRefFactory actorRefFactory, final LoggingAdapter logger) {
        return new ChildActorNanny(checkNotNull(actorRefFactory, "actorRefFactory"), checkNotNull(logger, "logger"));
    }

    /**
     * Creates a child actor with the specified name in the context of this {@code ChildActorNanny}'s
     * {@code ActorRefFactory}.
     *
     * @param actorName the name of the child actor to create. This name must not be {@code null}, empty or start with
     * {@code "$"}.
     * @param actorProps the {@code Props} of the child actor to create.
     * @return the {@code ActorRef} of the created child actor.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code actorName} is empty.
     * @throws akka.actor.InvalidActorNameException if {@code actorName} is invalid or already in use.
     * @throws akka.ConfigurationException if deployment, dispatcher or mailbox configuration is wrong.
     * @throws UnsupportedOperationException if invoked on an ActorSystem that uses a custom user guardian.
     */
    public ActorRef startChildActor(final CharSequence actorName, final Props actorProps) {
        argumentNotEmpty(actorName, "actorName");
        checkNotNull(actorProps, "actorProps");

        final var encodedActorName = encodeAsUsAscii(actorName.toString());
        logger.debug("Starting child actor <{}> as <{}> …", actorName, encodedActorName);
        final var result = actorRefFactory.actorOf(actorProps, encodedActorName);
        if (logger.isDebugEnabled()) {
            logger.debug("Started child actor <{}>.", result.path());
        }
        return result;
    }

    private static String encodeAsUsAscii(final String actorName) {
        return URLEncoder.encode(actorName, StandardCharsets.US_ASCII);
    }

    /**
     * Creates a child actor in the context of this {@code ChildActorNanny}'s {@code ActorRefFactory}.
     * The specified name gets concatenated by an increasing count number for that name.
     * <p>
     * For example, if this method was called two times with the base actor name {@code "myActor"}, the actual child
     * actor would be
     * <ol>
     *     <li>{@code "myActor1"},</li>
     *     <li>{@code "myActor2"}.</li>
     * </ol>
     * The count numbers are maintained per base actor name.
     *
     * @param baseActorName the base name of the child actor to create. This name must not be {@code null}, empty or
     * start with {@code "$"}. The actual actor name differs in the way that it is concatenated with a count number.
     * @param actorProps the {@code Props} of the child actor to create.
     * @return the {@code ActorRef} of the created child actor.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code baseActorName} is empty.
     * @throws akka.actor.InvalidActorNameException if {@code actorName} is invalid or already in use.
     * @throws akka.ConfigurationException if deployment, dispatcher or mailbox configuration is wrong.
     * @throws UnsupportedOperationException if invoked on an ActorSystem that uses a custom user guardian.
     */
    public ActorRef startChildActorConflictFree(final CharSequence baseActorName, final Props actorProps) {
        return startChildActor(
                getNextChildActorName(argumentNotEmpty(baseActorName, "baseActorName").toString()),
                actorProps
        );
    }

    private String getNextChildActorName(final String baseActorName) {
        final var childActorCount = childActorCounts.computeIfAbsent(baseActorName, unused -> new AtomicInteger(0));
        return baseActorName + childActorCount.incrementAndGet();
    }

    /**
     * Asynchronously stops the actor pointed to by the specified {@code ActorRef} argument.
     *
     * @param childActorRef {@code ActorRef} to the actor to be stopped.
     * @throws NullPointerException if {@code childActorRef} is {@code null}.
     */
    public void stopChildActor(final ActorRef childActorRef) {
        checkNotNull(childActorRef, "childActorRef");
        if (logger.isDebugEnabled()) {
            logger.debug("Stopping child actor <{}>.", childActorRef.path());
        }
        actorRefFactory.stop(childActorRef);
    }

}
