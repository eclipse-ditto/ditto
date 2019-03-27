/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.models.concierge.batch;

import java.util.Map;
import java.util.function.BiFunction;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;
import org.eclipse.ditto.signals.base.GlobalErrorRegistry;
import org.eclipse.ditto.signals.commands.batch.BatchCommandRegistry;
import org.eclipse.ditto.signals.commands.batch.BatchCommandResponseRegistry;
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

        builder.add(GlobalErrorRegistry.getInstance());
        builder.add(BatchCommandRegistry.newInstance(batchStepCommandRegistry));
        builder.add(BatchCommandResponseRegistry.newInstance());
        builder.add(BatchEventRegistry.newInstance(batchStepCommandRegistry, batchStepCommandResponseRegistry));

        return builder.build();
    }

}
