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
package org.eclipse.ditto.signals.commands.devops;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.WithManifest;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Base interface for all devops commands which are understood by things, keystore and messages services.
 *
 * @param <T> the type of the implementing class.
 */
public interface DevOpsCommand<T extends DevOpsCommand> extends Jsonifiable.WithPredicate<JsonObject, JsonField>,
        WithDittoHeaders<T>, WithManifest {

    /**
     * Type Prefix of DevOps commands.
     */
    String TYPE_PREFIX = "things.devops.commands:";

    /**
     * Returns the type of this command.
     *
     * @return the type of this command
     */
    String getType();

    /**
     * Returns the name of the command. This is gathered by the type of the command in the default implementation.
     *
     * @return the command name.
     */
    default String getName() {
        return getType().contains(":") ? getType().split(":")[1] : getType();
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    @Override
    default JsonSchemaVersion getImplementedSchemaVersion() {
        return getDittoHeaders().getSchemaVersion().orElse(getLatestSchemaVersion());
    }

    /**
     * Returns all non hidden marked fields of this command.
     *
     * @return a JSON object representation of this command including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    /**
     * An enumeration of the known {@link JsonField}s of a {@code DevOpsCommand}.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the command's type.
         */
        public static final JsonFieldDefinition<String> TYPE =
                JsonFactory.newStringFieldDefinition("type", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
