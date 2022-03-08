/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.model;

import java.time.Instant;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * Contains common state/behavior shared by {@link ThingDescription} and {@link ThingModel}.
 *
 * @param <T> the type of the ThingSkeleton.
 * @since 2.4.0
 */
public interface ThingSkeleton<T extends ThingSkeleton<T>> extends TypedJsonObject<T>, Jsonifiable<JsonObject> {

    AtContext getAtContext();

    Optional<AtType> getAtType();

    Optional<IRI> getId();

    Optional<Title> getTitle();

    Optional<Titles> getTitles();

    Optional<Description> getDescription();

    Optional<Descriptions> getDescriptions();

    Optional<Version> getVersion();

    Optional<IRI> getBase();

    Optional<Links> getLinks();

    Optional<Properties> getProperties();

    Optional<Actions> getActions();

    Optional<Events> getEvents();

    Optional<RootForms> getForms();

    Optional<UriVariables> getUriVariables();

    Optional<SecurityDefinitions> getSecurityDefinitions();

    Optional<SchemaDefinitions> getSchemaDefinitions();

    Optional<IRI> getSupport();

    Optional<Instant> getCreated();

    Optional<Instant> getModified();

    Optional<Security> getSecurity();

    Optional<Profile> getProfile();

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a Thing Description / Thing Model.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<String> AT_CONTEXT = JsonFactory.newStringFieldDefinition(
                "@context");

        public static final JsonFieldDefinition<JsonArray> AT_CONTEXT_MULTIPLE =
                JsonFactory.newJsonArrayFieldDefinition("@context");

        public static final JsonFieldDefinition<String> AT_TYPE = JsonFactory.newStringFieldDefinition(
                "@type");

        public static final JsonFieldDefinition<JsonArray> AT_TYPE_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "@type");

        public static final JsonFieldDefinition<String> ID = JsonFactory.newStringFieldDefinition(
                "id");

        public static final JsonFieldDefinition<String> TITLE = JsonFactory.newStringFieldDefinition(
                "title");

        public static final JsonFieldDefinition<JsonObject> TITLES = JsonFactory.newJsonObjectFieldDefinition(
                "titles");

        public static final JsonFieldDefinition<JsonObject> PROPERTIES = JsonFactory.newJsonObjectFieldDefinition(
                "properties");

        public static final JsonFieldDefinition<JsonObject> ACTIONS = JsonFactory.newJsonObjectFieldDefinition(
                "actions");

        public static final JsonFieldDefinition<JsonObject> EVENTS = JsonFactory.newJsonObjectFieldDefinition(
                "events");

        public static final JsonFieldDefinition<String> DESCRIPTION = JsonFactory.newStringFieldDefinition(
                "description");

        public static final JsonFieldDefinition<JsonObject> DESCRIPTIONS = JsonFactory.newJsonObjectFieldDefinition(
                "descriptions");

        public static final JsonFieldDefinition<JsonObject> VERSION = JsonFactory.newJsonObjectFieldDefinition(
                "version");

        public static final JsonFieldDefinition<JsonArray> LINKS = JsonFactory.newJsonArrayFieldDefinition(
                "links");

        public static final JsonFieldDefinition<String> BASE = JsonFactory.newStringFieldDefinition(
                "base");

        public static final JsonFieldDefinition<JsonArray> FORMS = JsonFactory.newJsonArrayFieldDefinition(
                "forms");

        public static final JsonFieldDefinition<JsonObject> URI_VARIABLES =
                JsonFactory.newJsonObjectFieldDefinition("uriVariables");

        public static final JsonFieldDefinition<JsonObject> SECURITY_DEFINITIONS =
                JsonFactory.newJsonObjectFieldDefinition("securityDefinitions");

        public static final JsonFieldDefinition<JsonObject> SCHEMA_DEFINITIONS =
                JsonFactory.newJsonObjectFieldDefinition("schemaDefinitions");

        public static final JsonFieldDefinition<String> SUPPORT = JsonFactory.newStringFieldDefinition(
                "support");

        public static final JsonFieldDefinition<String> CREATED = JsonFactory.newStringFieldDefinition(
                "created");

        public static final JsonFieldDefinition<String> MODIFIED = JsonFactory.newStringFieldDefinition(
                "modified");

        public static final JsonFieldDefinition<String> SECURITY = JsonFactory.newStringFieldDefinition(
                "security");

        public static final JsonFieldDefinition<JsonArray> SECURITY_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "security");

        public static final JsonFieldDefinition<String> PROFILE = JsonFactory.newStringFieldDefinition(
                "profile");

        public static final JsonFieldDefinition<JsonArray> PROFILE_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "profile");

        private JsonFields() {
            throw new AssertionError();
        }

    }
}
