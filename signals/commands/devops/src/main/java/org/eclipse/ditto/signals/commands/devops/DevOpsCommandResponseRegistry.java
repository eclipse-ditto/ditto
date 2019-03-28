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
package org.eclipse.ditto.signals.commands.devops;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.signals.base.AbstractJsonParsableRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.base.JsonParsableRegistry;

/**
 * A {@link JsonParsableRegistry} aware of all {@link DevOpsCommandResponse}s.
 */
@Immutable
public final class DevOpsCommandResponseRegistry extends AbstractJsonParsableRegistry<DevOpsCommandResponse>
        implements JsonParsableRegistry<DevOpsCommandResponse> {

    private DevOpsCommandResponseRegistry(final Map<String, JsonParsable<DevOpsCommandResponse>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code DevOpsCommandRegistry}.
     *
     * @return the registry.
     */
    public static DevOpsCommandResponseRegistry newInstance() {
        final Map<String, JsonParsable<DevOpsCommandResponse>> parseStrategies = new HashMap<>();

        parseStrategies.put(ChangeLogLevelResponse.TYPE, ChangeLogLevelResponse::fromJson);
        parseStrategies.put(RetrieveLoggerConfigResponse.TYPE, RetrieveLoggerConfigResponse::fromJson);
        parseStrategies.put(RetrieveStatisticsResponse.TYPE, RetrieveStatisticsResponse::fromJson);
        parseStrategies.put(RetrieveStatisticsDetailsResponse.TYPE, RetrieveStatisticsDetailsResponse::fromJson);
        parseStrategies.put(AggregatedDevOpsCommandResponse.TYPE, (jsonObject, dittoHeaders) ->
                AggregatedDevOpsCommandResponse.fromJson(jsonObject, dittoHeaders, parseStrategies));

        return new DevOpsCommandResponseRegistry(parseStrategies);
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        return jsonObject.getValueOrThrow(DevOpsCommandResponse.JsonFields.TYPE);
    }

}
