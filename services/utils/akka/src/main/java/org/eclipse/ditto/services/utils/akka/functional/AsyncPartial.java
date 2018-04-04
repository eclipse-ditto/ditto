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
package org.eclipse.ditto.services.utils.akka.functional;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;


// TODO: see if this is replaceable by GraphStage.
// TODO: javadoc
@FunctionalInterface
public interface AsyncPartial<A, B> extends Function<A, CompletionStage<Optional<B>>> {

    CompletionStage<Optional<B>> apply(A a);

    static <A, B> AsyncPartial<A, B> fromTotal(final Function<? super A, ? extends B> f) {
        return a -> CompletableFuture.completedFuture(Optional.of(f.apply(a)));
    }

    static <A, B> AsyncPartial<A, B> fromAsyncTotal(final Function<? super A, CompletionStage<? extends B>> f) {
        return a -> f.apply(a).thenApply(Optional::of);
    }

    default <D> AsyncPartial<A, D> then(final Function<? super B, CompletionStage<Optional<D>>> next) {
        return a -> apply(a).thenCompose(result -> {
            if (result.isPresent()) {
                return next.apply(result.get());
            } else {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        });
    }

    default AsyncPartial<A, B> filter(final Predicate<A> predicate) {
        return a -> predicate.test(a)
                ? apply(a)
                : CompletableFuture.completedFuture(Optional.empty());
    }

    default <C> AsyncPartial<C, B> filterBy(final Class<A> clazz) {
        return x -> clazz.isInstance(x)
                ? apply(clazz.cast(x))
                : CompletableFuture.completedFuture(Optional.empty());
    }

    default AsyncPartial<A, B> orElse(final Function<A, CompletionStage<Optional<B>>> fallback) {
        return a -> apply(a).thenCompose(result -> {
            if (result.isPresent()) {
                return CompletableFuture.completedFuture(result);
            } else {
                return fallback.apply(a);
            }
        });
    }

    default Total<A, CompletionStage<B>> withDefault(final Function<A, B> fallback) {
        return a -> apply(a).thenApply(result ->
                result.orElseGet(() -> fallback.apply(a)));
    }
}
