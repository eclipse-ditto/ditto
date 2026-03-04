/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.ditto.policies.model.signals.commands.checkpermissions.CheckPermissionsResponse;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.JsonifiableMapper;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for {@link CheckPermissionsResponse}.
 *
 * @since 3.9.0
 */
final class CheckPermissionsCommandResponseMappingStrategies
        extends AbstractMappingStrategies<CheckPermissionsResponse> {

    private static final CheckPermissionsCommandResponseMappingStrategies INSTANCE =
            new CheckPermissionsCommandResponseMappingStrategies();

    private CheckPermissionsCommandResponseMappingStrategies() {
        super(initMappingStrategies());
    }

    static CheckPermissionsCommandResponseMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<CheckPermissionsResponse>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<CheckPermissionsResponse>> mappingStrategies = new HashMap<>();
        mappingStrategies.put(CheckPermissionsResponse.TYPE,
                adaptable -> {
                    final JsonObject permissionResults = adaptable.getPayload().getValue()
                            .filter(JsonValue::isObject)
                            .map(JsonValue::asObject)
                            .orElse(JsonObject.empty());
                    final Map<String, Boolean> resultMap = new LinkedHashMap<>();
                    permissionResults.forEach(field ->
                            resultMap.put(field.getKey().toString(), field.getValue().asBoolean()));
                    return CheckPermissionsResponse.of(resultMap, dittoHeadersFrom(adaptable));
                });
        return mappingStrategies;
    }
}
