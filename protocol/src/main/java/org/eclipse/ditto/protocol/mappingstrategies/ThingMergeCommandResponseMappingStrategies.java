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
package org.eclipse.ditto.protocol.mappingstrategies;

import java.util.Collections;
import java.util.Map;

import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThingResponse;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for thing modify command responses.
 */
final class ThingMergeCommandResponseMappingStrategies
        extends AbstractThingMappingStrategies<MergeThingResponse> {

    private static final ThingMergeCommandResponseMappingStrategies INSTANCE =
            new ThingMergeCommandResponseMappingStrategies();

    private ThingMergeCommandResponseMappingStrategies() {
        super(initMappingStrategies());
    }

    private static Map<String, JsonifiableMapper<MergeThingResponse>> initMappingStrategies() {
        return Collections.singletonMap(MergeThingResponse.TYPE,
                AdaptableToSignalMapper.of(MergeThingResponse.class,
                        context -> {
                            final Adaptable adaptable = context.getAdaptable();
                            final Payload payload = adaptable.getPayload();
                            return MergeThingResponse.newInstance(context.getThingId(),
                                    payload.getPath(),
                                    context.getHttpStatusOrThrow(),
                                    context.getDittoHeaders());
                        }));
    }

    static ThingMergeCommandResponseMappingStrategies getInstance() {
        return INSTANCE;
    }

}
