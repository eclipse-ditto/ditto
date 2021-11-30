/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.updater.actors;

import java.util.List;

import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.common.config.SearchConfig;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

/**
 * Thing event observer to be loaded by reflection.
 * Can be used as an extension point to use process thing events.
 * Implementations MUST have a public constructor taking an actorSystem as argument.
 *
 * @since 2.3.0
 */
public abstract class ThingEventObserver implements Extension {

    private static final ThingEventObserver.ExtensionId EXTENSION_ID = new ThingEventObserver.ExtensionId();

    /**
     * Load a {@code ThingEventObserver} dynamically according to the search configuration.
     *
     * @param actorSystem The actor system in which to load the observer.
     * @return The thing event observer.
     */
    public static ThingEventObserver get(final ActorSystem actorSystem) {
        return EXTENSION_ID.get(actorSystem);
    }

    /**
     * Process the given {@code ThingEvent}.
     *
     * @param event the thing event
     */
    public abstract void processThingEvent(final ThingEvent<?> event);


    /**
     * ID of the actor system extension to validate the {@code ThingEventObserver}.
     */
    private static final class ExtensionId extends AbstractExtensionId<ThingEventObserver> {

        @Override
        public ThingEventObserver createExtension(final ExtendedActorSystem system) {
            final SearchConfig searchConfig =
                    DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()));

            return AkkaClassLoader.instantiate(system, ThingEventObserver.class,
                    searchConfig.getThingEventObserverImplementation(), List.of(ActorSystem.class), List.of(system));
        }
    }

}
