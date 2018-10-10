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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.exceptions.NamespaceBlockedException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.signals.base.WithId;

/**
 * Behavior that blocks messages directed toward entities in a cached namespace.
 */
public final class BlockNamespaceBehavior {

    private final Cache<String, Object> namespaceCache;

    private BlockNamespaceBehavior(final Cache<String, Object> namespaceCache) {
        this.namespaceCache = namespaceCache;
    }

    /**
     * Create a namespace-blocking behavior.
     *
     * @param namespaceCache the cache to read namespaces from.
     * @return the namespace-blocking behavior.
     */
    public static BlockNamespaceBehavior of(final Cache<String, Object> namespaceCache) {
        return new BlockNamespaceBehavior(namespaceCache);
    }

    /**
     * Blocks a {@code message} if it relates to an entity within a blocked namespace.
     *
     * @param message the message to block.
     * @return a completion stage which either completes successfully with the given {@code message} or exceptionally
     * with a {@code NamespaceBlockedException}.
     */
    public CompletionStage<WithDittoHeaders> block(final WithDittoHeaders message) {
        if (message instanceof WithId) {
            final Optional<String> namespaceOptional =
                    NamespaceCacheWriter.namespaceFromId(((WithId) message).getId());
            if (namespaceOptional.isPresent()) {
                final String namespace = namespaceOptional.get();
                return namespaceCache.getIfPresent(namespace)
                        .thenApply(cachedNamespace -> {
                            if (cachedNamespace.isPresent()) {
                                throw NamespaceBlockedException.newBuilder(namespace)
                                        .dittoHeaders(message.getDittoHeaders())
                                        .build();
                            } else {
                                return message;
                            }
                        });
            }
        }
        return CompletableFuture.completedFuture(message);
    }

}
