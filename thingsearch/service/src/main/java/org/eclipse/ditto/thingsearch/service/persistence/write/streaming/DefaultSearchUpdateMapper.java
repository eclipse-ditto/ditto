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

import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.javadsl.Source;

/**
 * Default {@code SearchUpdateMapper} for custom search update processing.
 */
public final class DefaultSearchUpdateMapper extends SearchUpdateMapper {

    /**
     * Instantiate this provider. Called by reflection.
     */
    protected DefaultSearchUpdateMapper(final ActorSystem actorSystem) {
        super(actorSystem);
        // Nothing to initialize.
    }

    @Override
    public Source<List<AbstractWriteModel>, NotUsed> processWriteModels(final List<AbstractWriteModel> writeModels) {
        return Source.single(writeModels);
    }

}
