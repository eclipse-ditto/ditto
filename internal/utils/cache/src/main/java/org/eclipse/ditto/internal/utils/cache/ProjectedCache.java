/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.cache;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * A cache working on an embedded passed {@code cache} with values of type {@code <V>}, representing a projected cache
 * with values of type {@code U}.
 * <p>
 * Conversion between the embedded cache and projected cache are performed via functions passed to the class'
 * constructor.
 *
 * @param <K> the type of the cache's key
 * @param <U> the type of the projected cache's value
 * @param <V> the type of the embedded cache's value
 */
final class ProjectedCache<K, U, V> implements Cache<K, U> {

    private final Cache<K, V> cache;
    private final Function<V, U> project;
    private final Function<Optional<V>, Optional<U>> projectOptional;
    private final Function<U, V> embed;

    ProjectedCache(final Cache<K, V> cache, final Function<V, U> project, final Function<U, V> embed) {
        this.cache = cache;
        this.project = project;
        this.embed = embed;
        projectOptional = optional -> optional.map(project);
    }

    @Override
    public CompletableFuture<Optional<U>> get(final K key) {
        return cache.get(key).thenApply(projectOptional);
    }

    @Override
    public CompletableFuture<Optional<U>> getIfPresent(final K key) {
        return cache.getIfPresent(key).thenApply(projectOptional);
    }

    @Override
    public Optional<U> getBlocking(final K key) {
        return projectOptional.apply(cache.getBlocking(key));
    }

    @Override
    public boolean invalidate(final K key) {
        return cache.invalidate(key);
    }

    @Override
    public void put(final K key, final U value) {
        cache.put(key, embed.apply(value));
    }

    @Override
    public ConcurrentMap<K, U> asMap() {
        final ConcurrentMap<K, U> concurrentMap = new ConcurrentHashMap<>();
        cache.asMap().forEach((key, value) -> concurrentMap.put(key, project.apply(value)));
        return concurrentMap;
    }
}
