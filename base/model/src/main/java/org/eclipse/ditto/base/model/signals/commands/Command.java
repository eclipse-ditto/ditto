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
package org.eclipse.ditto.base.model.signals.commands;

import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.atteo.classindex.IndexSubclasses;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * Base Interface for all commands which are understood by Ditto.
 *
 * @param <T> the type of the implementing class.
 */
@IndexSubclasses
public interface Command<T extends Command<T>> extends Signal<T> {

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
     * Returns all non-hidden marked fields of this command.
     *
     * @return a JSON object representation of this command including only non-hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    JsonObject toJson(JsonSchemaVersion schemaVersion, Predicate<JsonField> predicate);

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
         * Category of commands that change the state of entities.
         */
        MERGE,

        /**
         * Category of commands that delete entities.
         */
        DELETE,

        /**
         * Category of commands that are neither of the above 3 (query, modify, delete) but perform an action on the
         * entity.
         */
        ACTION
    }

    /**
     * This class contains common definitions for all fields of a {@code Command}'s JSON representation.
     * Implementation of {@code Command} may add additional fields by extending this class.
     */
    @Immutable
    abstract class JsonFields {

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

}
