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
package org.eclipse.ditto.internal.utils.extension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint.ExtensionId.ExtensionIdConfig;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

public final class DittoExtensionIds implements Extension {

    static final ExtensionId INSTANCE = new ExtensionId();
    private final Map<DittoExtensionPoint.ExtensionId.ExtensionIdConfig<?>, DittoExtensionPoint.ExtensionId<?>> extensionIds = new HashMap<>();

    public <T extends Extension> DittoExtensionPoint.ExtensionId<T> computeIfAbsent(
            final ExtensionIdConfig<T> extensionIdConfig,
            final Function<ExtensionIdConfig<T>, DittoExtensionPoint.ExtensionId<T>> extensionIdCreator) {

        final DittoExtensionPoint.ExtensionId<T> extensionId =
                (DittoExtensionPoint.ExtensionId<T>) extensionIds.get(extensionIdConfig);
        if (extensionId == null) {
            final DittoExtensionPoint.ExtensionId<T> newExtensionId = extensionIdCreator.apply(extensionIdConfig);
            extensionIds.put(extensionIdConfig, newExtensionId);
            return newExtensionId;
        } else {
            return extensionId;
        }
    }

    public static DittoExtensionIds get(final ActorSystem actorSystem) {
        return INSTANCE.get(actorSystem);
    }

    static final class ExtensionId extends AbstractExtensionId<DittoExtensionIds> {

        @Override
        public DittoExtensionIds createExtension(final ExtendedActorSystem system) {

            return AkkaClassLoader.instantiate(system, DittoExtensionIds.class,
                    DittoExtensionIds.class.getCanonicalName(),
                    List.of(),
                    List.of());
        }
    }

}
