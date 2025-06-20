/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.devops.commands;

import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.devops.WithWotValidationConfigId;

/**
 * Base interface for all commands which are handled by the WoT validation config persistence.
 *
 * @param <T> the type of the implementing class.
 * @since 3.8.0
 */
public interface WotValidationConfigCommand<T extends WotValidationConfigCommand<T>> extends Command<T>,
        WithWotValidationConfigId {

    /**
     * Type Prefix of WoT validation config commands.
     */
    String TYPE_PREFIX = "wot.validation.config." + TYPE_QUALIFIER + ":";

    /**
     * Resource type of WoT validation config commands.
     */
    String RESOURCE_TYPE = "wot-validation-config";

    /**
     * This class contains definitions for all specific fields of a WoT validation config command's JSON representation.
     */
    class JsonFields {

        /**
         * JSON field containing the validation config.
         */
        public static final JsonFieldDefinition<JsonValue> VALIDATION_CONFIG =
                JsonFactory.newJsonValueFieldDefinition("validationConfig", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the config ID.
         */
        public static final JsonFieldDefinition<String> CONFIG_ID =
                JsonFactory.newStringFieldDefinition("configId", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }

    /**
     * Returns the entity type of the WoT validation config.
     *
     * @return the entity type.
     */
    default EntityType getEntityType() {
        return EntityType.of(RESOURCE_TYPE);
    }
} 