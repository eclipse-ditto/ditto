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
package org.eclipse.ditto.services.gateway.security.cache;

import static java.util.Objects.requireNonNull;

import java.security.PublicKey;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CaffeineCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;

/**
 * This cache holds the last recently used {@link PublicKey}s which are identified by a {@link PublicKeyIdWithIssuer}.
 * The maximum size of the cache can be configured.
 */
public class PublicKeyCache implements Cache<PublicKeyIdWithIssuer, PublicKey> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicKeyCache.class);

    private final Cache<PublicKeyIdWithIssuer, PublicKey> delegate;

    private PublicKeyCache(final Cache<PublicKeyIdWithIssuer, PublicKey> delegate) {
        this.delegate = delegate;
    }

    /**
     * Returns a new instance of {@code PublicKeyCache}.
     *
     * @param maxCacheEntries the maximum amount of entries in the returned cache.
     * @param expiry the expiry of entries.
     * @param loader the algorithm used for loading public keys.
     * @param namedMetricRegistry the named {@link MetricRegistry} for cache statistics.
     * @return a new PublicKeyCache.
     */
    public static Cache<PublicKeyIdWithIssuer, PublicKey> newInstance(final int maxCacheEntries,
            final Duration expiry, final AsyncCacheLoader<PublicKeyIdWithIssuer, PublicKey> loader,
            final Map.Entry<String, MetricRegistry> namedMetricRegistry) {

        requireNonNull(expiry);
        requireNonNull(loader);
        requireNonNull(namedMetricRegistry);

        return new PublicKeyCache(createDelegateCache(maxCacheEntries, expiry, loader, namedMetricRegistry));
    }

    private static Cache<PublicKeyIdWithIssuer, PublicKey> createDelegateCache(final int maxCacheEntries,
            final Duration expiry, final AsyncCacheLoader<PublicKeyIdWithIssuer, PublicKey> loader,
            final Map.Entry<String, MetricRegistry> namedMetricRegistry) {

        final Caffeine<PublicKeyIdWithIssuer, PublicKey> caffeine = Caffeine.newBuilder()
                .maximumSize(maxCacheEntries)
                .expireAfterWrite(expiry.getSeconds(), TimeUnit.SECONDS)
                .removalListener(new CacheRemovalListener());
        return CaffeineCache.of(caffeine, loader, namedMetricRegistry);
    }

    @Override
    public CompletableFuture<Optional<PublicKey>> get(final PublicKeyIdWithIssuer keyIdWithIssuer) {
        return delegate.get(keyIdWithIssuer);
    }

    @Override
    public Optional<PublicKey> getBlocking(final PublicKeyIdWithIssuer keyIdWithIssuer) {
        return delegate.getBlocking(keyIdWithIssuer);
    }

    @Override
    public void invalidate(final PublicKeyIdWithIssuer keyIdWithIssuer) {
        delegate.invalidate(keyIdWithIssuer);
    }

    private static final class CacheRemovalListener implements RemovalListener<PublicKeyIdWithIssuer, PublicKey> {

        @Override
        public void onRemoval(@Nullable final PublicKeyIdWithIssuer key, @Nullable final PublicKey value,
                @Nonnull final com.github.benmanes.caffeine.cache.RemovalCause cause) {
            final String msgTemplate = "Removed PublicKey with ID <{}> from cache due to cause '{}'.";
            LOGGER.debug(msgTemplate, key, cause);
        }
    }

}
