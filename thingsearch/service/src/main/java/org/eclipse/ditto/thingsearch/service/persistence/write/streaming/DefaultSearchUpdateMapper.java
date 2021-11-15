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

import org.bson.BsonDocument;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;

import com.mongodb.client.model.WriteModel;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.javadsl.Source;

/**
 * Default {@code SearchUpdateMapper} for custom search update processing.
 */
public final class DefaultSearchUpdateMapper extends SearchUpdateMapper {

    private final ThreadSafeDittoLogger logger = DittoLoggerFactory.getThreadSafeLogger(getClass());

    /**
     * Instantiate this provider. Called by reflection.
     */
    @SuppressWarnings("unused")
    private DefaultSearchUpdateMapper(final ActorSystem actorSystem) {
        super(actorSystem);
        // Nothing to initialize.
    }

    @Override
    public Source<List<Pair<AbstractWriteModel, WriteModel<BsonDocument>>>, NotUsed>
    processWriteModels(final List<AbstractWriteModel> writeModels) {

        return Source.single(writeModels).mapAsync(1, models -> toIncrementalMongo(models, logger));
    }

}
