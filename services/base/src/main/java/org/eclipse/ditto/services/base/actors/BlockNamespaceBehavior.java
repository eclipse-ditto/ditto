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
package org.eclipse.ditto.services.base.actors;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.signals.base.WithId;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

/**
 * Actor behavior that blocks messages directed toward entities in a cached namespace.
 *
 * @param <T> type of replies for blocked namespaces.
 */
public final class BlockNamespaceBehavior<T> {

    private final Cache<String, Object> namespaceCache;
    private final BiFunction<String, WithDittoHeaders, T> errorCreator;

    private BlockNamespaceBehavior(final Cache<String, Object> namespaceCache,
            final BiFunction<String, WithDittoHeaders, T> errorCreator) {

        this.namespaceCache = namespaceCache;
        this.errorCreator = errorCreator;
    }

    /**
     * Create a namespace-blocking behavior.
     *
     * @param namespaceCache the cache to read namespaces from.
     * @param errorCreator creator of replies for blocked messages.
     * @param <T> type of replies for blocked messages.
     * @return the namespace-blocking behavior.
     */
    public static <T> BlockNamespaceBehavior<T> of(final Cache<String, Object> namespaceCache,
            final BiFunction<String, WithDittoHeaders, T> errorCreator) {

        return new BlockNamespaceBehavior<>(namespaceCache, errorCreator);
    }

    /**
     * Create a namespace-blocking behavior that delivers the blocked namespace.
     *
     * @param namespaceCache the cache to read namespaces from.
     * @return the namespace-blocking behavior.
     */
    public static BlockNamespaceBehavior<String> of(final Cache<String, Object> namespaceCache) {

        return new BlockNamespaceBehavior<>(namespaceCache, (ns, msg) -> ns);
    }

    /**
     * Apply namespace-blocking behavior on a message.
     *
     * @param withDittoHeaders the incoming message.
     * @return either a reply for blocked messages or the incoming message itself.
     */
    public CompletionStage<Either<T, WithDittoHeaders>> apply(final WithDittoHeaders withDittoHeaders) {
        if (withDittoHeaders instanceof WithId) {
            final Optional<String> namespaceOptional =
                    NamespaceCacheWriter.namespaceFromId(((WithId) withDittoHeaders).getId());
            if (namespaceOptional.isPresent()) {
                // check namespace
                final String namespace = namespaceOptional.get();
                return namespaceCache.getIfPresent(namespace)
                        .thenApply(cachedNamespace -> cachedNamespace
                                .<Either<T, WithDittoHeaders>>map(cachedValue ->
                                        Left.apply(errorCreator.apply(namespace, withDittoHeaders)))
                                .orElseGet(() -> Right.apply(withDittoHeaders)));
            }
        }
        return CompletableFuture.completedFuture(Right.apply(withDittoHeaders));
    }

    /**
     * Use the namespace-blocking behavior as a pre-enforcer function.
     *
     * @return a function that attempts to block its argument. Returns a future of the argument if it is
     * not blocked or throw a runtime exception if it is.
     */
    public Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> asPreEnforcer() {
        return withDittoHeaders -> apply(withDittoHeaders).thenApply(result -> {
            if (result.isRight()) {
                return result.right().get();
            } else {
                final Object errorMessage = result.left().get();
                if (errorMessage instanceof RuntimeException) {
                    throw (RuntimeException) errorMessage;
                } else {
                    final String message = String.format("Message <%s> blocked due to <%s>",
                            withDittoHeaders.toString(), errorMessage.toString());
                    throw new IllegalArgumentException(message);
                }
            }
        });
    }

    /**
     * Use the namespace-blocking behavior for actor message routing.
     *
     * @param context context of the actor with the namespace-blocking behavior.
     * @param forwardRecipient recipient of messages that are not blocked.
     * @param <A> type of acknowledgement.
     * @return a consumer of messages that replies errors to senders of blocked messages and forwards not-blocked
     * messages to a fixed recipient.
     */
    public <A> Consumer<WithDittoHeaders> acknowledgeOrForward(final AbstractActor.ActorContext context,
            final ActorRef forwardRecipient,
            final Function<T, Optional<A>> ackMapper) {

        final ActorRef deadLetters = context.system().deadLetters();
        final ActorRef self = context.self();
        return message -> {
            final ActorRef sender = context.sender();
            apply(message).thenAccept(result -> result
                    .left().map(ackSenderUnlessDeadLetters(sender, deadLetters, self, ackMapper))
                    .right().map(forwardMessageToRecipient(sender, forwardRecipient)));
        };
    }

    private static <T, A> scala.Function1<T, T> ackSenderUnlessDeadLetters(final ActorRef sender,
            final ActorRef deadLetters,
            final ActorRef self,
            final Function<T, Optional<A>> ackMapper) {

        return message -> {
            if (!Objects.equals(sender, deadLetters)) {
                ackMapper.apply(message).ifPresent(ack -> {
                    sender.tell(ack, self);
                });
            }
            return message;
        };
    }

    private static <T> scala.Function1<T, T> forwardMessageToRecipient(final ActorRef sender,
            final ActorRef recipient) {
        return message -> {
            recipient.tell(message, sender);
            return message;
        };
    }
}
