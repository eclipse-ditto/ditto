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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import static scala.compat.java8.FutureConverters.toJava;
import static scala.compat.java8.FutureConverters.toScala;

import java.util.Optional;
import java.util.function.Function;

import org.eclipse.ditto.internal.utils.cache.CaffeineCache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

import akka.contrib.persistence.mongodb.MongoCollectionCache;
import scala.Function1;
import scala.concurrent.Future;

/**
 * Cache of futures of MongoDB collections of the official Scala driver..
 *
 * @param <C> type of MongoDB collections of the official Scala driver. Irrelevant for the cache.
 */
public final class DittoMongoCollectionCache<C> implements MongoCollectionCache<Future<C>> {

    private final CaffeineCache<String, C> cache;

    public DittoMongoCollectionCache(final Config config) {
        final String ttl = "expire-after-write";
        final String maxSize = "max-size";

        final Caffeine<Object, Object> caffeine = Caffeine.newBuilder();
        tryLookup(ttl, config::getDuration).ifPresent(caffeine::expireAfterWrite);
        tryLookup(maxSize, config::getLong).ifPresent(caffeine::maximumSize);
        cache = CaffeineCache.of(caffeine);
    }

    @Override
    public Future<C> getOrElseCreate(final String collectionName,
            final Function1<String, Future<C>> collectionCreator) {

        return toScala(cache.get(collectionName, (key, ec) ->
                toJava(collectionCreator.apply(key)).toCompletableFuture()));
    }

    @Override
    public void invalidate(final String collectionName) {
        cache.invalidate(collectionName);
    }

    private static <T> Optional<T> tryLookup(final String key, final Function<String, T> lookup) {
        try {
            return Optional.of(lookup.apply(key));
        } catch (final ConfigException e) {
            return Optional.empty();
        }
    }

}
