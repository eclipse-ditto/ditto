/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.namespaces;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.namespaces.NamespaceBlockedException;
import org.eclipse.ditto.model.namespaces.NamespaceReader;
import org.eclipse.ditto.signals.base.WithId;

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
     * Blocks a {@code signal} if it relates to an entity within a blocked namespace.
     *
     * @param signal the signal to block.
     * @return a completion stage which either completes successfully with the given {@code signal} or exceptionally
     * with a {@code NamespaceBlockedException}.
     */
    public CompletionStage<WithDittoHeaders> block(final WithDittoHeaders signal) {
        if (signal instanceof WithId) {
            final Optional<String> namespaceOptional = NamespaceReader.fromEntityId(((WithId) signal).getId());
            if (namespaceOptional.isPresent()) {
                final String namespace = namespaceOptional.get();
                return blockedNamespaces.contains(namespace)
                        .thenApply(containsNamespace -> {
                            if (containsNamespace != null && containsNamespace) {
                                throw NamespaceBlockedException.newBuilder(namespace)
                                        .dittoHeaders(signal.getDittoHeaders())
                                        .build();
                            } else {
                                return signal;
                            }
                        });
            }
        }
        return CompletableFuture.completedFuture(signal);
    }

}
