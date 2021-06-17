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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import java.util.List;

import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.common.config.SearchConfig;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

/**
 * Search Update Mapper to be loaded by reflection.
 * Can be used as an extension point to use custom map search updates.
 * Implementations MUST have a public constructor taking an actorSystem as argument.
 *
 * @since 2.1.0
 */
public abstract class SearchUpdateMapper implements Extension {

    private static final ExtensionId EXTENSION_ID = new ExtensionId();

    protected final ActorSystem actorSystem;

    protected SearchUpdateMapper(final ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Gets the write models of the search updates and processes them.
     * <p>
     * Should not throw an exception. If a exception is thrown, the mapping is ignored.
     * If no search update should be executed an empty list can be returned.
     */
    public abstract List<AbstractWriteModel> processWriteModels(final List<AbstractWriteModel> writeModels);

    /**
     * Load a {@code SearchUpdateListener} dynamically according to the search configuration.
     *
     * @param actorSystem The actor system in which to load the listener.
     * @return The listener.
     */
    public static SearchUpdateMapper get(final ActorSystem actorSystem) {
        return EXTENSION_ID.get(actorSystem);
    }

    /**
     * ID of the actor system extension to validate the {@code SearchUpdateListener}.
     */
    private static final class ExtensionId extends AbstractExtensionId<SearchUpdateMapper> {

        @Override
        public SearchUpdateMapper createExtension(final ExtendedActorSystem system) {
            final SearchConfig searchConfig =
                    DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(
                            system.settings().config()));

            return AkkaClassLoader.instantiate(system, SearchUpdateMapper.class,
                    searchConfig.getSearchUpdateMapperImplementation(),
                    List.of(ActorSystem.class),
                    List.of(system));
        }
    }

}
