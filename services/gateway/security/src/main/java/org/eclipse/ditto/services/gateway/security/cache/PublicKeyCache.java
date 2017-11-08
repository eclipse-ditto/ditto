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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotNull;

import java.security.PublicKey;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

/**
 * This cache holds the last recently used {@link PublicKey}s which are identified by their Key ID. The maximum size of
 * the cache can be configured.
 */
public class PublicKeyCache implements Cache<String, PublicKey> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicKeyCache.class);

    private static final int CACHE_CONCURRENCY_LEVEL = 8;

    private final com.google.common.cache.Cache<String, PublicKey> cache;

    private PublicKeyCache(final com.google.common.cache.Cache<String, PublicKey> theCache) {
        cache = theCache;
    }

    /**
     * Returns a new instance of {@code PublicKeyCache}.
     *
     * @param maxCacheEntries the maximum amount of entries in the returned cache.
     * @param expiry the expiry of entries.
     * @return a new PublicKeyCache.
     */
    public static Cache<String, PublicKey> newInstance(final int maxCacheEntries, final Duration expiry) {
        return new PublicKeyCache(initCache(maxCacheEntries, argumentNotNull(expiry)));
    }

    private static com.google.common.cache.Cache<String, PublicKey> initCache(final int maxCacheEntries,
            final Duration expiry) {
        return CacheBuilder.newBuilder()
                .maximumSize(maxCacheEntries)
                .expireAfterWrite(expiry.getSeconds(), TimeUnit.SECONDS)
                .concurrencyLevel(CACHE_CONCURRENCY_LEVEL)
                .removalListener(new CacheRemovalListener())
                .build();
    }

    private static void checkKeyId(final String keyId) {
        final String msgTemplate = "The ID of the PublicKey to get must not be {0}!";
        requireNonNull(keyId, MessageFormat.format(msgTemplate, "null"));
        checkArgument(!keyId.isEmpty(), MessageFormat.format(msgTemplate, "empty"));
    }

    @Override
    public Optional<PublicKey> get(final String keyId) {
        checkKeyId(keyId);

        final PublicKey foundSolutionCacheEntryOrNull = cache.getIfPresent(keyId);
        return Optional.ofNullable(foundSolutionCacheEntryOrNull);
    }

    @Override
    public void put(final String keyId, final PublicKey key) {
        checkKeyId(keyId);
        requireNonNull(key, "The PublicKey to put into this cache must not be null!");

        LOGGER.debug("Caching PublicKey '{}' ...", key);
        cache.put(keyId, key);
    }

    @Override
    public boolean remove(final String keyId) {
        requireNonNull(keyId, "The keyId must not be null!");
        LOGGER.debug("Removing cached PublicKey for Key ID from cache: '{}'", keyId);
        if (cache.getIfPresent(keyId) != null) {
            cache.invalidate(keyId);
            return true;
        } else {
            return false;
        }
    }

    private static final class CacheRemovalListener implements RemovalListener<String, PublicKey> {

        @Override
        public void onRemoval(final RemovalNotification<String, PublicKey> notification) {
            final RemovalCause cause = notification.getCause();
            final String publicKeyId = notification.getKey();
            final String msgTemplate = "Removed PublicKey with ID <{}> from cache due to cause '{}'.";
            switch (cause) {
                case REPLACED:
                    // log nothing here
                    break;
                case EXPLICIT:
                case EXPIRED:
                    LOGGER.debug(msgTemplate, publicKeyId, cause);
                    break;
                default:
                    LOGGER.info(msgTemplate, publicKeyId, cause);
            }
        }
    }

}
