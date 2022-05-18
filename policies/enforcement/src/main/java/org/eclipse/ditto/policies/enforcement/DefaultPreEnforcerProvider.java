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
package org.eclipse.ditto.policies.enforcement;

import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.internal.utils.namespaces.BlockNamespaceBehavior;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.policies.enforcement.placeholders.PlaceholderSubstitution;
import org.eclipse.ditto.policies.enforcement.validators.CommandWithOptionalEntityValidator;

import akka.actor.ActorSystem;

public class DefaultPreEnforcerProvider implements PreEnforcerProvider{

    private final ActorSystem actorSystem;

    public DefaultPreEnforcerProvider(final ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    @Override
    public <T extends DittoHeadersSettable<?>> PreEnforcer<T> getPreEnforcer() {
        return dittoHeadersSettable ->
                BlockNamespaceBehavior.of(BlockedNamespaces.of(actorSystem))
                        .block(dittoHeadersSettable)
                        .thenApply(CommandWithOptionalEntityValidator.createInstance())
                        .thenApply(PreEnforcer::setOriginatorHeader)
                        .thenCompose(PlaceholderSubstitution.newInstance());
    }
}
