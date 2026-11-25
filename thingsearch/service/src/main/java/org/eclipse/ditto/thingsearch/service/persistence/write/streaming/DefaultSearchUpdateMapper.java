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

import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;

import com.typesafe.config.Config;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Source;

/**
 * Default {@code SearchUpdateMapper} for custom search update processing. Passes the neutral write model
 * through unchanged; the per-backend seam performs the incremental-update computation.
 */
public final class DefaultSearchUpdateMapper extends SearchUpdateMapper {

    /**
     * Instantiate this provider. Called by reflection.
     * @param actorSystem the actor system in which to load the extension.
     * @param config the configuration for this extension.
     */
    @SuppressWarnings("unused")
    private DefaultSearchUpdateMapper(final ActorSystem actorSystem, final Config config) {
        super(actorSystem, config);
        // Nothing to initialize.
    }

    @Override
    public Source<AbstractWriteModel, NotUsed> processWriteModel(final AbstractWriteModel writeModel,
            final AbstractWriteModel lastWriteModel) {
        return Source.single(writeModel);
    }

}
