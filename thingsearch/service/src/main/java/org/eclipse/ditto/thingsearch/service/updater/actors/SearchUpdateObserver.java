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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;

import akka.actor.ActorSystem;

/**
 * Search update observer to be loaded by reflection.
 * Can be used as an extension point to observe search updates.
 * Implementations MUST have a public constructor taking an actorSystem as argument.
 *
 * @since 2.3.0
 */
public interface SearchUpdateObserver extends DittoExtensionPoint {

    /**
     * Process the given {@code Metadata} and thing as {@code JsonObject}.
     *
     * @param metadata the metadata for the update.
     * @param thingJson the thing used for the update as jsonObject.
     */
    public void process(final Metadata metadata, @Nullable final JsonObject thingJson);

    /**
     * Loads the implementation of {@code SearchUpdateObserver} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code SearchUpdateObserver} should be loaded.
     * @return the {@code SearchUpdateObserver} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static SearchUpdateObserver get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        final var implementation = DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(
                actorSystem.settings().config())).getSearchUpdateObserverImplementation();

        return AkkaClassLoader.instantiate(actorSystem, SearchUpdateObserver.class,
                implementation,
                List.of(ActorSystem.class),
                List.of(actorSystem));
    }

}
