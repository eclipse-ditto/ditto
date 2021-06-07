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
package org.eclipse.ditto.services.thingsearch.persistence.write.streaming;

import java.util.List;

import org.eclipse.ditto.services.thingsearch.common.config.DittoSearchConfig;
import org.eclipse.ditto.services.thingsearch.common.config.SearchConfig;
import org.eclipse.ditto.services.thingsearch.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.services.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

/**
 * Search Update Listener to be loaded by reflection.
 * Can be used as an extension point to use custom listeners for search updates.
 * Implementations MUST have a public constructor taking an actorSystem as argument.
 *
 * @since 2.1.0
 */
public abstract class SearchUpdateListener implements Extension {

    private static final ExtensionId EXTENSION_ID = new ExtensionId();

    protected final ActorSystem actorSystem;

    protected SearchUpdateListener(final ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Gets the write models of the search updates and processes them.
     * <p>
     * May throw an exception depending on the implementation in the used {@code SearchUpdateListener}.
     */
    public abstract void processWriteModels(final List<AbstractWriteModel> writeModels);

    /**
     * Load a {@code SearchUpdateListener} dynamically according to the search configuration.
     *
     * @param actorSystem The actor system in which to load the listener.
     * @return The listener.
     */
    public static SearchUpdateListener get(final ActorSystem actorSystem) {
        return EXTENSION_ID.get(actorSystem);
    }

    /**
     * ID of the actor system extension to validate the {@code SearchUpdateListener}.
     */
    private static final class ExtensionId extends AbstractExtensionId<SearchUpdateListener> {

        @Override
        public SearchUpdateListener createExtension(final ExtendedActorSystem system) {
            final SearchConfig searchConfig =
                    DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(
                            system.settings().config()));

            return AkkaClassLoader.instantiate(system, SearchUpdateListener.class,
                    searchConfig.getSearchUpdateListenerImplementation(),
                    List.of(ActorSystem.class),
                    List.of(system));
        }
    }

}
