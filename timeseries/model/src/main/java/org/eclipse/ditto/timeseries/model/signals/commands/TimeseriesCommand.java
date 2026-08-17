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
package org.eclipse.ditto.timeseries.model.signals.commands;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;

/**
 * Aggregate marker interface for all commands handled by the Timeseries service. Commands target
 * Thing resources for policy enforcement (resource type {@value #RESOURCE_TYPE}) but are dispatched
 * to the Timeseries service via their {@code timeseries.commands:} type prefix.
 *
 * @param <T> the type of the implementing class.
 * @since 4.0.0
 */
public interface TimeseriesCommand<T extends TimeseriesCommand<T>> extends Command<T> {

    /**
     * Resource type used by the policy enforcer to look up the relevant {@code thing:/...}
     * resources. Timeseries commands grant/deny on the same resource tree as Thing commands.
     */
    String RESOURCE_TYPE = "thing";

    /**
     * Type prefix shared by all Timeseries commands (e.g.
     * {@code timeseries.commands:retrieveTimeseries}).
     */
    String TYPE_PREFIX = "timeseries." + Command.TYPE_QUALIFIER + ":";

    @Override
    default String getResourceType() {
        return RESOURCE_TYPE;
    }

    /**
     * JSON field definitions shared by Timeseries commands.
     */
    final class JsonFields extends Command.JsonFields {

        /**
         * The Thing ID a timeseries command targets.
         */
        public static final JsonFieldDefinition<String> JSON_THING_ID =
                JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
