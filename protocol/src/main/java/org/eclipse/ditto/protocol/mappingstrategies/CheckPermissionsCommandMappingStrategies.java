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
import java.util.Map;

import org.eclipse.ditto.policies.model.signals.commands.checkpermissions.CheckPermissions;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for {@link CheckPermissions} commands.
 *
 * @since 3.9.0
 */
final class CheckPermissionsCommandMappingStrategies extends AbstractMappingStrategies<CheckPermissions> {

    private static final CheckPermissionsCommandMappingStrategies INSTANCE =
            new CheckPermissionsCommandMappingStrategies();

    private CheckPermissionsCommandMappingStrategies() {
        super(initMappingStrategies());
    }

    static CheckPermissionsCommandMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<CheckPermissions>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<CheckPermissions>> mappingStrategies = new HashMap<>();
        mappingStrategies.put(CheckPermissions.TYPE,
                adaptable -> CheckPermissions.fromJson(payloadValueAsObject(adaptable),
                        dittoHeadersFrom(adaptable)));
        return mappingStrategies;
    }

    private static JsonObject payloadValueAsObject(final Adaptable adaptable) {
        return adaptable.getPayload().getValue()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElse(JsonObject.empty());
    }
}
