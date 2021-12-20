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

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.common.config.SearchConfig;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

/**
 * Search update observer to be loaded by reflection.
 * Can be used as an extension point to observe search updates.
 * Implementations MUST have a public constructor taking an actorSystem as argument.
 *
 * @since 2.3.0
 */
public abstract class SearchUpdateObserver implements Extension {

    private static final SearchUpdateObserver.ExtensionId EXTENSION_ID = new SearchUpdateObserver.ExtensionId();

    /**
     * Load a {@code SearchUpdateObserver} dynamically according to the search configuration.
     *
     * @param actorSystem the actor system in which to load the observer.
     * @return the thing event observer.
     */
    public static SearchUpdateObserver get(final ActorSystem actorSystem) {
        return EXTENSION_ID.get(actorSystem);
    }

    /**
     * Process the given {@code Metadata} and thing as {@code JsonObject}.
     *
     * @param metadata the metadata for the update.
     * @param thingJson the thing used for the update as jsonObject.
     */
    public abstract void process(final Metadata metadata, @Nullable final JsonObject thingJson);


    /**
     * ID of the actor system extension to validate the {@code SearchUpdateObserver}.
     */
    private static final class ExtensionId extends AbstractExtensionId<SearchUpdateObserver> {

        @Override
        public SearchUpdateObserver createExtension(final ExtendedActorSystem system) {
            final SearchConfig searchConfig =
                    DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()));

            return AkkaClassLoader.instantiate(system, SearchUpdateObserver.class,
                    searchConfig.getSearchUpdateObserverImplementation(), List.of(ActorSystem.class), List.of(system));
        }
    }

}
