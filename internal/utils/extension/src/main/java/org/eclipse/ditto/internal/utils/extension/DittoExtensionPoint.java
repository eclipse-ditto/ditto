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

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

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
    abstract class ExtensionId<T extends akka.actor.Extension> extends AbstractExtensionId<T> {

        private final ExtensionIdConfig<T> extensionIdConfig;

        /**
         * Returns the {@code ExtensionId} for the implementation that should be loaded.
         *
         * @param extensionIdConfig configuration for the extension ID.
         */
        protected ExtensionId(final ExtensionIdConfig<T> extensionIdConfig) {
            this.extensionIdConfig = extensionIdConfig;
        }

        @Override
        public T createExtension(final ExtendedActorSystem system) {
            return AkkaClassLoader.instantiate(system, extensionIdConfig.parentClass(),
                    getImplementation(system),
                    List.of(ActorSystem.class, Config.class),
                    List.of(system, extensionIdConfig.extensionConfig()));
        }

        protected ExtensionIdConfig<T> globalConfig(final ActorSystem actorSystem) {
            return ExtensionIdConfig.of(
                    extensionIdConfig.parentClass(),
                    actorSystem.settings().config(),
                    getConfigPath()
            );
        }

        protected String getImplementation(final ExtendedActorSystem actorSystem) {
            final String extensionClass;
            if (extensionIdConfig.extensionClass() == null) {
                // Resolve from default extension config
                final ExtensionIdConfig<T> globalConfig = globalConfig(actorSystem);
                extensionClass = globalConfig.extensionClass();
                if (extensionClass == null) {
                    throw new DittoConfigError(MessageFormat.format(
                            "Could not resolve default extension class at config key <{0}>.",
                            getConfigPath()
                    ));
                }
            } else {
                extensionClass = extensionIdConfig.extensionClass();
            }
            return extensionClass;
        }

        private String getConfigPath() {
            return ScopedConfig.DITTO_EXTENSIONS_SCOPE + "." + getConfigKey();
        }

        protected abstract String getConfigKey();

        public record ExtensionIdConfig<T extends Extension>(Class<T> parentClass,
                                                             @Nullable String extensionClass,
                                                             Config extensionConfig) {

            private static final String EXTENSION_CLASS = "extension-class";
            private static final String EXTENSION_CONFIG = "extension-config";

            /**
             * @param parentClass the parent class of the extension that should be initialized.
             * @param config the config to load the extension from.
             * @param configKey the configuration key on root level of the given config.
             * @param <T> The type of the extension that should be initialized.
             * @return the extension id config.
             * @throws com.typesafe.config.ConfigException.WrongType in case neither an object nor a string is
             * configured at the config key of config.
             */
            public static <T extends akka.actor.Extension> ExtensionIdConfig<T> of(
                    final Class<T> parentClass,
                    final Config config,
                    final String configKey) {

                if (config.hasPath(configKey)) {
                    final var configValue = config.getValue(configKey);
                    return of(parentClass, configValue);
                }
                return new ExtensionIdConfig<>(parentClass, null, ConfigFactory.empty());
            }

            @SuppressWarnings("unchecked")
            public static <T extends Extension> ExtensionIdConfig<T> of(final Class<T> parentClass,
                    final ConfigValue configValue) {

                final var valueType = configValue.valueType();
                final Object unwrappedValue = configValue.unwrapped();
                if (valueType == ConfigValueType.OBJECT) {
                    // means that the entry is a Map which can be used to create config object from
                    return ofObjectConfig(parentClass, ConfigFactory.parseMap((Map<String, ?>) unwrappedValue));
                } else {
                    // Allows shorthand configuration by just defining the fqcn if no extension config is desired.
                    return ofStringConfig(parentClass, (String) unwrappedValue);
                }
            }

            private static <T extends Extension> ExtensionIdConfig<T> ofStringConfig(
                    final Class<T> parentClass,
                    final String extensionClass) {

                return new ExtensionIdConfig<>(parentClass, extensionClass, ConfigFactory.empty());
            }

            private static <T extends Extension> ExtensionIdConfig<T> ofObjectConfig(
                    final Class<T> parentClass,
                    final Config config) {

                @Nullable final String extensionClass;
                final Config extensionConfig;
                if (config.hasPath(EXTENSION_CLASS)) {
                    extensionClass = config.getString(EXTENSION_CLASS);
                } else {
                    extensionClass = null;
                }
                if (config.hasPath(EXTENSION_CONFIG)) {
                    extensionConfig = config.getConfig(EXTENSION_CONFIG);
                } else {
                    extensionConfig = ConfigFactory.empty();
                }
                return new ExtensionIdConfig<>(parentClass, extensionClass, extensionConfig);
            }

            @Override
            public boolean equals(final Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                final ExtensionIdConfig<?> that = (ExtensionIdConfig<?>) o;
                return parentClass.equals(that.parentClass) &&
                        extensionConfig.equals(that.extensionConfig) &&
                        Objects.equals(extensionClass, that.extensionClass);
            }

            @Override
            public int hashCode() {
                return Objects.hash(parentClass, extensionClass, extensionConfig);
            }

            @Override
            public String toString() {
                return getClass().getSimpleName() + " [" +
                        "parentClass=" + parentClass +
                        ", extensionClass=" + extensionClass +
                        ", extensionConfig=" + extensionConfig +
                        "]";
            }

        }

    }

}
