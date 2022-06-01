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
package org.eclipse.ditto.policies.enforcement.pre_enforcement;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.internal.utils.namespaces.BlockNamespaceBehavior;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;

import akka.actor.ActorSystem;

public final class BlockedNamespacePreEnforcer implements PreEnforcer {

    final BlockNamespaceBehavior blockNamespaceBehavior;

    public BlockedNamespacePreEnforcer(final ActorSystem actorSystem) {
        blockNamespaceBehavior = BlockNamespaceBehavior.of(BlockedNamespaces.of(actorSystem));
    }

    @Override
    public CompletionStage<DittoHeadersSettable<?>> apply(final DittoHeadersSettable<?> t) {
        return blockNamespaceBehavior.block(t);
    }
}
