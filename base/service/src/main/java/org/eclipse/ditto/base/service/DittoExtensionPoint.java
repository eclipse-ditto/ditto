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
package org.eclipse.ditto.base.service;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

/**
 * Extension Point for extending Ditto via Akka Extensions to provide custom functionality to different
 * aspects of the service.
 *
 * @since 3.0.0
 */
public interface DittoExtensionPoint extends Extension {

    /**
     * @param <T> the class of the extension for which an implementation should be loaded.
     */
    abstract class ExtensionId<T extends Extension> extends AbstractExtensionId<T> {

        private final ExtensionIdConfig<T> extensionIdConfig;

        /**
         * Returns the {@code ExtensionId} for the implementation that should be loaded.
         *
         * @param parentClass the class of the extensions for which an implementation should be loaded.
         * @param extensionIdConfig configuration for the extension ID.
         */
        protected ExtensionId(final ExtensionIdConfig<T> extensionIdConfig) {
            this.extensionIdConfig = extensionIdConfig;
        }

        protected ExtensionId(final Class<T> parentClass) {
            this(new ExtensionIdConfig<>(parentClass, null, ConfigFactory.empty()));
        }

        @Override
        public T createExtension(final ExtendedActorSystem system) {
            return AkkaClassLoader.instantiate(system, extensionIdConfig.parentClass,
                    getImplementation(system),
                    List.of(ActorSystem.class, Config.class),
                    List.of(system, extensionIdConfig.extensionConfig));
        }

        protected String getImplementation(final ExtendedActorSystem actorSystem) {
            if (extensionIdConfig.extensionClass == null) {
                final Object anyRef = actorSystem.settings().config().getAnyRef(getConfigPath());
                if (anyRef instanceof Map<?, ?> map) {
                    return map.get("extension-class").toString();
                } else {
                    return anyRef.toString();
                }
            } else {
                return extensionIdConfig.extensionClass;
            }
        }

        protected abstract String getConfigPath();

        public record ExtensionIdConfig<T extends Extension>(Class<T> parentClass,
                                                             @Nullable String extensionClass,
                                                             Config extensionConfig) {

            public static <T extends Extension> ExtensionIdConfig<T> of(
                    final Class<T> parentClass,
                    final Config config) {

                @Nullable final String extensionClass;
                final Config extensionConfig;
                if (config.hasPath("extension-class")) {
                    extensionClass = config.getString("extension-class");
                } else {
                    extensionClass = null;
                }
                if (config.hasPath("extension-config")) {
                    extensionConfig = config.getConfig("extension-config");
                } else {
                    extensionConfig = ConfigFactory.empty();
                }
                return new ExtensionIdConfig<>(parentClass, extensionClass, extensionConfig);
            }
        }

    }

}
