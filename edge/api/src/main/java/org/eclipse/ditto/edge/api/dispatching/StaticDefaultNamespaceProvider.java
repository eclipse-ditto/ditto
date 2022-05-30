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
package org.eclipse.ditto.edge.api.dispatching;

import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.policies.enforcement.config.DefaultEntityCreationConfig;
import org.eclipse.ditto.policies.enforcement.config.EntityCreationConfig;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;

import akka.actor.ActorSystem;

public final class StaticDefaultNamespaceProvider implements DefaultNamespaceProvider {

    private final String defaultNamespace;

    public StaticDefaultNamespaceProvider(final ActorSystem actorSystem) {
        final EntityCreationConfig entityCreationConfig = DefaultEntityCreationConfig.of(
                DefaultScopedConfig.dittoScoped(actorSystem.settings().config())
        );
        defaultNamespace = entityCreationConfig.getDefaultNamespace();
    }

    @Override
    public String getDefaultNamespace(final CreateThing createThing) {
        return defaultNamespace;
    }

    @Override
    public String getDefaultNamespace(final CreatePolicy createPolicy) {
        return defaultNamespace;
    }

}
