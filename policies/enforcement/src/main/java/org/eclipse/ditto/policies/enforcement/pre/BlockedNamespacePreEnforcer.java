/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement.pre;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.namespaces.BlockNamespaceBehavior;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Pre-Enforcer for blocking commands to blocked namespaces
 */
public final class BlockedNamespacePreEnforcer implements PreEnforcer {

    final BlockNamespaceBehavior blockNamespaceBehavior;

    /**
     * Constructs a new instance of BlockedNamespacePreEnforcer extension.
     *
     * @param actorSystem the actor system in which to load the extension.
     * @param config the configuration for this extension.
     */
    @SuppressWarnings("unused")
    public BlockedNamespacePreEnforcer(final ActorSystem actorSystem, final Config config) {
        blockNamespaceBehavior = BlockNamespaceBehavior.of(BlockedNamespaces.of(actorSystem));
    }

    @Override
    public CompletionStage<Signal<?>> apply(final Signal<?> signal) {
        return blockNamespaceBehavior.block(signal);
    }
}
