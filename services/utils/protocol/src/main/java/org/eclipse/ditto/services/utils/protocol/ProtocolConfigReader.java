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
package org.eclipse.ditto.services.utils.protocol;

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.services.base.config.AbstractConfigReader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.actor.DynamicAccess;
import akka.actor.ExtendedActorSystem;
import scala.Tuple2;
import scala.collection.JavaConverters;
import scala.reflect.ClassTag;
import scala.reflect.ClassTag$;
import scala.util.Try;

/**
 * Configuration reader for the path {@code ditto.headers}.
 */
public final class ProtocolConfigReader extends AbstractConfigReader {

    private static final String PATH = "ditto.protocol";

    private ProtocolConfigReader(final Config config) {
        super(config);
    }

    /**
     * Create a headers configuration reader from an unrelativized configuration object.
     *
     * @param rawConfig the raw configuration.
     * @return a headers configuration reader.
     */
    public static ProtocolConfigReader fromRawConfig(final Config rawConfig) {
        final Config headersConfig = rawConfig.hasPath(PATH)
                ? rawConfig.getConfig(PATH)
                : ConfigFactory.empty();
        return new ProtocolConfigReader(headersConfig);
    }

    /**
     * Return the configured class name of the provider of the Ditto header translator.
     *
     * @return the class name.
     */
    public String provider() {
        return getIfPresent("provider", config::getString)
                .orElse(DittoProtocolAdapterProvider.class.getCanonicalName());
    }

    /**
     * Load the configured protocol adapter provider by reflection.
     * Call the 1-argument constructor every subclass of {@link ProtocolAdapterProvider} should implement.
     *
     * @param actorSystem Akka actor system to perform reflection with.
     * @return the loaded protocol adapter provider.
     */
    public ProtocolAdapterProvider loadProtocolAdapterProvider(final ActorSystem actorSystem) {
        final String className = provider();
        final ClassTag<ProtocolAdapterProvider> tag = ClassTag$.MODULE$.apply(ProtocolAdapterProvider.class);
        final List<Tuple2<Class<?>, Object>> constructorArgs =
                Collections.singletonList(new Tuple2<>(getClass(), this));
        final DynamicAccess dynamicAccess = ((ExtendedActorSystem) actorSystem).dynamicAccess();
        final Try<ProtocolAdapterProvider> providerBox = dynamicAccess.createInstanceFor(className,
                JavaConverters.asScalaBuffer(constructorArgs).toList(), tag);
        return providerBox.get();
    }

    /**
     * Return headers that should be ignored by Ditto.
     *
     * @return headers that should be ignored.
     */
    public List<String> blacklist() {
        return getIfPresent("blacklist", config::getStringList)
                .orElse(Collections.emptyList());
    }

    /**
     * Return headers required by any previous version of Ditto protocol adapter.
     *
     * @return headers whose removal breaks compatibility.
     */
    public List<String> incompatibleBlacklist() {
        return getIfPresent("incompatible-blacklist", config::getStringList)
                .orElse(Collections.emptyList());
    }
}
