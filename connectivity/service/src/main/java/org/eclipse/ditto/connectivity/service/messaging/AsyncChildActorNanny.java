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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;

/**
 * Provides asynchronous {@link ChildActorNanny} methods for concurrent access.
 * Wraps a {@link ChildActorNannyActor}.
 */
public final class AsyncChildActorNanny {

    /**
     * Timeout waiting for a child actor to start.
     */
    public static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ActorRef childActorNannyActor;

    private AsyncChildActorNanny(final ActorRef childActorNannyActor) {
        this.childActorNannyActor = childActorNannyActor;
    }

    /**
     * Returns a new instance of {@code AsyncChildActorNanny}.
     *
     * @param childActorNanny the nanny with which the {@code ChildActorNannyActor} is created.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AsyncChildActorNanny newInstance(final ChildActorNanny childActorNanny) {
        checkNotNull(childActorNanny, "childActorNanny");
        final ActorRef childActorNannyActor =
                childActorNanny.startChildActorConflictFree("childActorNanny", ChildActorNannyActor.props());
        return new AsyncChildActorNanny(childActorNannyActor);
    }

    /**
     * Returns a new instance of {@code AsyncChildActorNanny} for test.
     *
     * @param actorSystem Actor system in which to create the {@code ChildActorNannyActor}.
     * @param nannyToTest The {@code ChildActorNanny} to use to create actors.
     * @return The new instance.
     * @throws java.lang.NullPointerException if any argument is {@code null}.
     */
    public static AsyncChildActorNanny newInstanceForTest(final ActorSystem actorSystem,
            final ChildActorNanny nannyToTest) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(nannyToTest, "childActorNanny");
        final ActorRef childActorNannyActor = actorSystem.actorOf(ChildActorNannyActor.propsForTest(nannyToTest));
        return new AsyncChildActorNanny(childActorNannyActor);
    }

    /**
     * Creates a child actor in the context of the {@code ChildActorNannyActor}.
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
     */
    public CompletionStage<ActorRef> startChildActorConflictFree(final CharSequence baseActorName,
            final Props actorProps) {
        final var message = new ChildActorNannyActor.StartChildActorConflictFree(baseActorName, actorProps);
        return Patterns.ask(childActorNannyActor, message, TIMEOUT).thenApply(ActorRef.class::cast);
    }

    /**
     * Asynchronously stops the actor wrapped by this object.
     * All subsequent calls to {@code startChildActorConflictFree} will fail.
     */
    public void stop() {
        childActorNannyActor.tell(new ChildActorNannyActor.StopThisActor(), ActorRef.noSender());
    }

}
