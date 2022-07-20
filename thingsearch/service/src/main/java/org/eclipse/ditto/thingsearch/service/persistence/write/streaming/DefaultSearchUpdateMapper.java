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

import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.updater.actors.MongoWriteModel;

import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.javadsl.Source;

/**
 * Default {@code SearchUpdateMapper} for custom search update processing.
 */
public final class DefaultSearchUpdateMapper extends SearchUpdateMapper {

    private final ThreadSafeDittoLogger logger = DittoLoggerFactory.getThreadSafeLogger(getClass());

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
    public Source<MongoWriteModel, NotUsed> processWriteModel(final AbstractWriteModel writeModel,
            final AbstractWriteModel lastWriteModel) {
        return toIncrementalMongo(writeModel, lastWriteModel, logger);
    }

}
