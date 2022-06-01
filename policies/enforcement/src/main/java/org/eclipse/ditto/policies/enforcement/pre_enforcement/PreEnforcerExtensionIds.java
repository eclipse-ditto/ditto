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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;

import akka.actor.AbstractExtensionId;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

final class PreEnforcerExtensionIds implements Extension {

    static final ExtensionId INSTANCE = new ExtensionId();
    private final Map<String, PreEnforcer.ExtensionId> extensionIds = new HashMap<>();

    PreEnforcer.ExtensionId get(final String implementation) {
        return extensionIds.computeIfAbsent(implementation, PreEnforcer.ExtensionId::new);
    }

    static final class ExtensionId extends AbstractExtensionId<PreEnforcerExtensionIds> {

        @Override
        public PreEnforcerExtensionIds createExtension(final ExtendedActorSystem system) {

            return AkkaClassLoader.instantiate(system, PreEnforcerExtensionIds.class,
                    PreEnforcerExtensionIds.class.getCanonicalName(),
                    List.of(),
                    List.of());
        }
    }

}
