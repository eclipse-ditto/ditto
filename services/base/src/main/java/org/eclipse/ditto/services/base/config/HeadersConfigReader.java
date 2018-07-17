/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.base.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.model.base.headers.DittoHeaderPublisherProvider;
import org.eclipse.ditto.model.base.headers.HeaderPublisher;
import org.eclipse.ditto.model.base.headers.HeaderPublisherProvider;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import scala.Tuple2;
import scala.collection.JavaConversions;
import scala.reflect.ClassTag;
import scala.reflect.ClassTag$;
import scala.util.Try;

/**
 * Configuration reader for the path {@code ditto.headers}.
 */
public final class HeadersConfigReader extends AbstractConfigReader {

    private static final String PATH = "ditto.headers";

    private HeadersConfigReader(final Config config) {
        super(config);
    }

    /**
     * Create a headers configuration reader from an unrelativized configuration object.
     *
     * @param rawConfig the raw configuration.
     * @return a headers configuration reader.
     */
    public static HeadersConfigReader fromRawConfig(final Config rawConfig) {
        final Config headersConfig = rawConfig.hasPath(PATH)
                ? rawConfig.getConfig(PATH)
                : ConfigFactory.empty();
        return new HeadersConfigReader(headersConfig);
    }

    /**
     * Return whether compatibility mode is on.
     *
     * @return whether compatibility mode is on.
     */
    public boolean compatibilityMode() {
        return getIfPresent("compatibility-mode", config::getBoolean)
                .orElse(false);
    }

    /**
     * Return headers that should not be published to clients.
     *
     * @return headers that should not be published.
     */
    public List<String> blacklist() {
        return compatibilityMode()
                ? compatibleBlacklist()
                : completeBlacklist();
    }

    /**
     * Return the configured class name of the provider of the Ditto header publisher.
     *
     * @return the class name.
     */
    public String publisherProvider() {
        return getIfPresent("publisher-provider", config::getString)
                .orElse(DittoHeaderPublisherProvider.class.getCanonicalName());
    }

    /**
     * Load the configured header publisher by reflection.
     *
     * @param actorSystem Akka actor system to perform reflection with.
     * @return the loaded header publisher.
     */
    public HeaderPublisher loadHeaderPublisher(final ActorSystem actorSystem) {
        final String className = publisherProvider();
        final ClassTag<HeaderPublisherProvider> tag = ClassTag$.MODULE$.apply(HeaderPublisherProvider.class);
        final List<Tuple2<Class<?>, Object>> constructorArgs = new ArrayList<>();
        final Try<HeaderPublisherProvider> providerBox =
                ((ExtendedActorSystem) actorSystem).dynamicAccess()
                        .createInstanceFor(className, JavaConversions.asScalaBuffer(constructorArgs).toList(), tag);
        return providerBox.get().get();
    }

    /**
     * Load the configured header publisher in compatibility mode by reflection.
     *
     * @param actorSystem Akka actor system to perform reflection with.
     * @return the loaded header publisher.
     */
    public HeaderPublisher loadCompatibleHeaderPublisher(final ActorSystem actorSystem) {
        return loadHeaderPublisher(actorSystem).forgetHeaderKeys(incompatibleBlacklist());
    }

    private List<String> incompatibleBlacklist() {
        return getIfPresent("incompatible-blacklist", config::getStringList)
                .orElse(Collections.emptyList());
    }

    private List<String> compatibleBlacklist() {
        return getIfPresent("blacklist", config::getStringList)
                .orElse(Collections.emptyList());
    }

    private List<String> completeBlacklist() {
        return Stream.concat(compatibleBlacklist().stream(), incompatibleBlacklist().stream())
                .collect(Collectors.toList());
    }
}
