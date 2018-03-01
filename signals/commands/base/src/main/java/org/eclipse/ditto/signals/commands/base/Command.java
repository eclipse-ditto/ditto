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
package org.eclipse.ditto.signals.commands.base;

import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Base Interface for all commands which are understood by Ditto.
 *
 * @param <T> the type of the implementing class.
 */
public interface Command<T extends Command> extends Signal<T> {

    /**
     * Type qualifier of commands.
     */
    String TYPE_QUALIFIER = "commands";

    /**
     * Returns the type prefix of this command.
     *
     * @return the prefix.
     */
    String getTypePrefix();

    /**
     * Returns the category this command belongs to.
     *
     * @return the command category.
     */
    Category getCategory();

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

    @Override
    JsonObject toJson(JsonSchemaVersion schemaVersion, Predicate<JsonField> predicate);

    /**
     * This class contains common definitions for all fields of a {@code Command}'s JSON representation.
     * Implementation of {@code Command} may add additional fields by extending this class.
     */
    @Immutable
    abstract class JsonFields {

        /**
         * JSON field containing the command's identification as String.
         */
        public static final JsonFieldDefinition<String> ID =
                JsonFactory.newStringFieldDefinition("command", FieldType.REGULAR, JsonSchemaVersion.V_1);

        /**
         * JSON field containing the command's type as String.
         */
        public static final JsonFieldDefinition<String> TYPE =
                JsonFactory.newStringFieldDefinition("type", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * Constructs a new {@code JsonFields} object.
         */
        protected JsonFields() {
            super();
        }

    }

    /**
     * Categories every command is classified into.
     */
    @Immutable
    enum Category {
        /**
         * Category of commands that do not change the state of any entity.
         */
        QUERY,

        /**
         * Category of commands that change the state of entities.
         */
        MODIFY,

        /**
         * Category of commands that delete entities.
         */
        DELETE
    }

}
