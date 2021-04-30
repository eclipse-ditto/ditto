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
package org.eclipse.ditto.gateway.service.health;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Utility methods for working with {@link java.util.concurrent.CompletableFuture}.
 */
final class CompletableFutureUtils {

    private CompletableFutureUtils() {
        throw new AssertionError();
    }

    /**
     * Collects the results of the given list of completable futures in a single completable future providing a list of
     * all results. This solution is inspired by
     * <a href="Stackoverflow">https://stackoverflow.com/questions/35809827/java-8-completablefuture-allof-with-collection-or-list</a>.
     *
     * @param futures the completable futures.
     * @param <T> the type of the results.
     * @return the resulting single completable future.
     */
    static <T> CompletableFuture<List<T>> collectAsList(final List<CompletableFuture<T>> futures) {
        return collect(futures, Collectors.toList());
    }

    private static <T, A, R> CompletableFuture<R> collect(final List<CompletableFuture<T>> futures,
            final Collector<T, A, R> collector) {

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).collect(collector));
    }

}
