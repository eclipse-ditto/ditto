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
import java.util.function.Function;
import java.util.function.Predicate;

// TODO: javadoc
@FunctionalInterface
public interface Partial<A, B> extends Function<A, Optional<B>> {

    Optional<B> apply(A a);

    static <A, B> Partial<A, B> fromTotal(final Function<? super A, ? extends B> f) {
        return a -> Optional.of(f.apply(a));
    }

    default <D> Partial<A, D> then(final Function<? super B, Optional<D>> next) {
        return a -> apply(a).flatMap(next);
    }

    default Partial<A, B> filter(final Predicate<A> predicate) {
        return a -> predicate.test(a)
                ? apply(a)
                : Optional.empty();
    }

    default <C> Partial<C, B> filterBy(final Class<A> clazz) {
        return x -> clazz.isInstance(x)
                ? apply(clazz.cast(x))
                : Optional.empty();
    }

    default Partial<A, B> orElse(final Function<A, Optional<B>> fallback) {
        return a -> apply(a)
                .map(Optional::of)
                .orElseGet(() -> fallback.apply(a));
    }

    default Total<A, B> withDefault(final Function<A, B> fallback) {
        return a -> apply(a).orElseGet(() -> fallback.apply(a));
    }

    default AsyncPartial<A, B> async() {
        return a -> CompletableFuture.completedFuture(apply(a));
    }
}
