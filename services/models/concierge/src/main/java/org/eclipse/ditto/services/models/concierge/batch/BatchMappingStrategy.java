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
package org.eclipse.ditto.services.models.concierge.batch;

import java.util.Map;
import java.util.function.BiFunction;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;
import org.eclipse.ditto.signals.commands.batch.BatchCommandRegistry;
import org.eclipse.ditto.signals.commands.batch.BatchCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.batch.exceptions.BatchErrorRegistry;
import org.eclipse.ditto.signals.events.batch.BatchEventRegistry;

/**
 * {@link MappingStrategy} for all {@link Jsonifiable} types required for ditto batch processing.
 */
@Immutable
public final class BatchMappingStrategy implements MappingStrategy {

    @Override
    public Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> determineStrategy() {
        final MappingStrategiesBuilder builder = MappingStrategiesBuilder.newInstance();

        final BatchStepCommandRegistry batchStepCommandRegistry = BatchStepCommandRegistry.newInstance();
        final BatchStepCommandResponseRegistry batchStepCommandResponseRegistry =
                BatchStepCommandResponseRegistry.newInstance();

        builder.add(BatchErrorRegistry.newInstance());
        builder.add(BatchCommandRegistry.newInstance(batchStepCommandRegistry));
        builder.add(BatchCommandResponseRegistry.newInstance());
        builder.add(BatchEventRegistry.newInstance(batchStepCommandRegistry, batchStepCommandResponseRegistry));

        return builder.build();
    }

}
