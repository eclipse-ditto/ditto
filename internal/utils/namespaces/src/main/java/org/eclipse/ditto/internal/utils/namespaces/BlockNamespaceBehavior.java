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
package org.eclipse.ditto.internal.utils.namespaces;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.namespaces.NamespaceBlockedException;
import org.eclipse.ditto.base.model.namespaces.NamespaceReader;

/**
 * Behavior that blocks messages directed toward entities in a cached namespace.
 */
public final class BlockNamespaceBehavior {

    private final BlockedNamespaces blockedNamespaces;

    private BlockNamespaceBehavior(final BlockedNamespaces blockedNamespaces) {
        this.blockedNamespaces = blockedNamespaces;
    }

    /**
     * Return an instance of {@code BlockNamespaceBehavior}.
     *
     * @param blockedNamespaces the cache to read namespaces from.
     * @return the namespace-blocking behavior.
     * @throws NullPointerException if {@code namespaceCache} is {@code null}.
     */
    public static BlockNamespaceBehavior of(final BlockedNamespaces blockedNamespaces) {
        return new BlockNamespaceBehavior(checkNotNull(blockedNamespaces, "blocked namespaces"));
    }

    /**
     * Blocks the given {@code signal} if it relates to an entity within a blocked namespace.
     *
     * @param signal the signal to block.
     * @param <T> the type of the signal to block.
     * @return a completion stage which either completes successfully with the given {@code signal} or exceptionally
     * with a {@code NamespaceBlockedException}.
     */
    public <T extends DittoHeadersSettable<?>> CompletionStage<T> block(final T signal) {
        if (signal instanceof WithEntityId withEntityId) {
            return isBlocked(withEntityId.getEntityId())
                    .thenApply(containsNamespace -> {
                        if (containsNamespace != null && containsNamespace) {
                            throw NamespaceBlockedException.newBuilder(
                                            NamespaceReader.fromEntityId(withEntityId.getEntityId()).orElseThrow()
                                    )
                                    .dittoHeaders(signal.getDittoHeaders())
                                    .build();
                        } else {
                            return signal;
                        }
                    });
        }
        return CompletableFuture.completedFuture(signal);
    }

    /**
     * Evaluates in the returned completionStage whether the passed {@code entityId}'s namespace is currently blocked
     * or not.
     *
     * @param entityId the entityId to extract the namespace from to check if it is blocked.
     * @return a completion stage which completes with {@code true} when the namespace of the passed {@code entityId}
     * is currently blocked and with {@code false} if not.
     */
    public CompletionStage<Boolean> isBlocked(final EntityId entityId) {
        final Optional<String> namespaceOptional = NamespaceReader.fromEntityId(entityId);
        if (namespaceOptional.isPresent()) {
            final String namespace = namespaceOptional.get();
            return blockedNamespaces.contains(namespace);
        } else {
            return CompletableFuture.completedFuture(false);
        }
    }

}
