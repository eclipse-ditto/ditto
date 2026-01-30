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
 * <p>
 * This interface provides access to the core vocabulary terms defined in the WoT Thing Description specification,
 * including metadata, interaction affordances, security configurations, and hypermedia controls.
 * </p>
 *
 * @param <T> the type of the ThingSkeleton.
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#thing">WoT TD Thing</a>
 * @since 2.4.0
 */
public interface ThingSkeleton<T extends ThingSkeleton<T>> extends TypedJsonObject<T>, Jsonifiable<JsonObject> {

    /**
     * Returns the JSON-LD {@code @context} that defines the semantics and vocabulary of the Thing Description or
     * Thing Model.
     *
     * @return the JSON-LD context.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#sec-context">WoT TD @context</a>
     */
    AtContext getAtContext();

    /**
     * Returns the optional JSON-LD {@code @type} that provides semantic annotations for the Thing, allowing
     * classification using ontology terms.
     *
     * @return the optional semantic type annotation.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#sec-semantic-annotations">WoT TD Semantic Annotations</a>
     */
    Optional<AtType> getAtType();

    /**
     * Returns the optional unique identifier of the Thing, typically an IRI.
     * <p>
     * The identifier is mandatory for Thing Descriptions but optional for Thing Models.
     * </p>
     *
     * @return the optional Thing identifier.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#thing">WoT TD Thing (id)</a>
     */
    Optional<IRI> getId();

    /**
     * Returns the optional human-readable title of the Thing, based on the default language.
     *
     * @return the optional title.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#titles-descriptions-serialization-json">WoT TD Human-Readable Metadata</a>
     */
    Optional<Title> getTitle();

    /**
     * Returns the optional multi-language map of human-readable titles for the Thing.
     *
     * @return the optional multi-language titles.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#multilanguage">WoT TD MultiLanguage</a>
     */
    Optional<Titles> getTitles();

    /**
     * Returns the optional human-readable description of the Thing, based on the default language.
     *
     * @return the optional description.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#titles-descriptions-serialization-json">WoT TD Human-Readable Metadata</a>
     */
    Optional<Description> getDescription();

    /**
     * Returns the optional multi-language map of human-readable descriptions for the Thing.
     *
     * @return the optional multi-language descriptions.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#multilanguage">WoT TD MultiLanguage</a>
     */
    Optional<Descriptions> getDescriptions();

    /**
     * Returns the optional version information of the Thing Description or Thing Model.
     *
     * @return the optional version information.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#versioninfo">WoT TD VersionInfo</a>
     */
    Optional<Version> getVersion();

    /**
     * Returns the optional base IRI that is used for resolving all relative IRIs in the Thing Description.
     *
     * @return the optional base IRI.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#thing">WoT TD Thing (base)</a>
     */
    Optional<IRI> getBase();

    /**
     * Returns the optional collection of web links to arbitrary resources related to the Thing.
     *
     * @return the optional links.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#link">WoT TD Link</a>
     */
    Optional<Links> getLinks();

    /**
     * Returns the optional collection of Property affordances that expose state of the Thing.
     *
     * @return the optional properties.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#propertyaffordance">WoT TD PropertyAffordance</a>
     */
    Optional<Properties> getProperties();

    /**
     * Returns the optional collection of Action affordances that describe functions that can be invoked on the Thing.
     *
     * @return the optional actions.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#actionaffordance">WoT TD ActionAffordance</a>
     */
    Optional<Actions> getActions();

    /**
     * Returns the optional collection of Event affordances that describe event sources of the Thing.
     *
     * @return the optional events.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#eventaffordance">WoT TD EventAffordance</a>
     */
    Optional<Events> getEvents();

    /**
     * Returns the optional collection of Form elements at the Thing level describing top-level operations.
     *
     * @return the optional root forms.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#form">WoT TD Form</a>
     */
    Optional<RootForms> getForms();

    /**
     * Returns the optional URI template variables that can be used in Form href expressions.
     *
     * @return the optional URI variables.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#thing">WoT TD Thing (uriVariables)</a>
     */
    Optional<UriVariables> getUriVariables();

    /**
     * Returns the optional collection of named security scheme definitions.
     * <p>
     * These definitions can be referenced by name in the {@code security} member.
     * </p>
     *
     * @return the optional security definitions.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#securityscheme">WoT TD SecurityScheme</a>
     */
    Optional<SecurityDefinitions> getSecurityDefinitions();

    /**
     * Returns the optional collection of schema definitions that can be used in data schemas.
     *
     * @return the optional schema definitions.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#thing">WoT TD Thing (schemaDefinitions)</a>
     */
    Optional<SchemaDefinitions> getSchemaDefinitions();

    /**
     * Returns the optional IRI pointing to information about the TD maintainer as URI scheme (e.g., mailto, tel, https).
     *
     * @return the optional support IRI.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#thing">WoT TD Thing (support)</a>
     */
    Optional<IRI> getSupport();

    /**
     * Returns the optional timestamp indicating when the Thing Description was created.
     *
     * @return the optional creation timestamp.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#thing">WoT TD Thing (created)</a>
     */
    Optional<Instant> getCreated();

    /**
     * Returns the optional timestamp indicating when the Thing Description was last modified.
     *
     * @return the optional modification timestamp.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#thing">WoT TD Thing (modified)</a>
     */
    Optional<Instant> getModified();

    /**
     * Returns the optional security configuration that must be satisfied to access the Thing.
     * <p>
     * The value references one or more named security schemes defined in {@link #getSecurityDefinitions()}.
     * </p>
     *
     * @return the optional security configuration.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#thing">WoT TD Thing (security)</a>
     */
    Optional<Security> getSecurity();

    /**
     * Returns the optional WoT profile identifier(s) indicating the Thing's conformance to specific TD profiles.
     *
     * @return the optional profile identifier(s).
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#thing">WoT TD Thing (profile)</a>
     */
    Optional<Profile> getProfile();

    /**
     * Validates that all prefixed terms (CURIEs) used in this ThingModel or ThingDescription
     * have their prefix defined in the {@code @context}.
     * <p>
     * A prefixed term has the format {@code prefix:localPart}, for example {@code ditto:category}
     * or {@code ace:ACESecurityScheme}. Standard WoT prefixes (like {@code tm}, {@code td}, {@code htv})
     * are allowed without explicit definition.
     * </p>
     *
     * @throws WotValidationException if undefined prefixes are detected.
     * @since 3.9.0
     */
    default void validateContextPrefixes() throws WotValidationException {
        AtContextPrefixValidator.validatePrefixes(this);
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a Thing Description / Thing Model.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the JSON-LD context (single value).
         */
        public static final JsonFieldDefinition<String> AT_CONTEXT = JsonFactory.newStringFieldDefinition(
                "@context");

        /**
         * JSON field definition for the JSON-LD context (multiple values).
         */
        public static final JsonFieldDefinition<JsonArray> AT_CONTEXT_MULTIPLE =
                JsonFactory.newJsonArrayFieldDefinition("@context");

        /**
         * JSON field definition for the JSON-LD type (single value).
         */
        public static final JsonFieldDefinition<String> AT_TYPE = JsonFactory.newStringFieldDefinition(
                "@type");

        /**
         * JSON field definition for the JSON-LD type (multiple values).
         */
        public static final JsonFieldDefinition<JsonArray> AT_TYPE_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "@type");

        /**
         * JSON field definition for the identifier.
         */
        public static final JsonFieldDefinition<String> ID = JsonFactory.newStringFieldDefinition(
                "id");

        /**
         * JSON field definition for the title.
         */
        public static final JsonFieldDefinition<String> TITLE = JsonFactory.newStringFieldDefinition(
                "title");

        /**
         * JSON field definition for the multilingual titles.
         */
        public static final JsonFieldDefinition<JsonObject> TITLES = JsonFactory.newJsonObjectFieldDefinition(
                "titles");

        /**
         * JSON field definition for the property affordances.
         */
        public static final JsonFieldDefinition<JsonObject> PROPERTIES = JsonFactory.newJsonObjectFieldDefinition(
                "properties");

        /**
         * JSON field definition for the action affordances.
         */
        public static final JsonFieldDefinition<JsonObject> ACTIONS = JsonFactory.newJsonObjectFieldDefinition(
                "actions");

        /**
         * JSON field definition for the event affordances.
         */
        public static final JsonFieldDefinition<JsonObject> EVENTS = JsonFactory.newJsonObjectFieldDefinition(
                "events");

        /**
         * JSON field definition for the description.
         */
        public static final JsonFieldDefinition<String> DESCRIPTION = JsonFactory.newStringFieldDefinition(
                "description");

        /**
         * JSON field definition for the multilingual descriptions.
         */
        public static final JsonFieldDefinition<JsonObject> DESCRIPTIONS = JsonFactory.newJsonObjectFieldDefinition(
                "descriptions");

        /**
         * JSON field definition for the version information.
         */
        public static final JsonFieldDefinition<JsonObject> VERSION = JsonFactory.newJsonObjectFieldDefinition(
                "version");

        /**
         * JSON field definition for the links.
         */
        public static final JsonFieldDefinition<JsonArray> LINKS = JsonFactory.newJsonArrayFieldDefinition(
                "links");

        /**
         * JSON field definition for the base IRI.
         */
        public static final JsonFieldDefinition<String> BASE = JsonFactory.newStringFieldDefinition(
                "base");

        /**
         * JSON field definition for the root-level forms.
         */
        public static final JsonFieldDefinition<JsonArray> FORMS = JsonFactory.newJsonArrayFieldDefinition(
                "forms");

        /**
         * JSON field definition for the URI template variables.
         */
        public static final JsonFieldDefinition<JsonObject> URI_VARIABLES =
                JsonFactory.newJsonObjectFieldDefinition("uriVariables");

        /**
         * JSON field definition for the security definitions.
         */
        public static final JsonFieldDefinition<JsonObject> SECURITY_DEFINITIONS =
                JsonFactory.newJsonObjectFieldDefinition("securityDefinitions");

        /**
         * JSON field definition for the schema definitions.
         */
        public static final JsonFieldDefinition<JsonObject> SCHEMA_DEFINITIONS =
                JsonFactory.newJsonObjectFieldDefinition("schemaDefinitions");

        /**
         * JSON field definition for the support contact URI.
         */
        public static final JsonFieldDefinition<String> SUPPORT = JsonFactory.newStringFieldDefinition(
                "support");

        /**
         * JSON field definition for the creation timestamp.
         */
        public static final JsonFieldDefinition<String> CREATED = JsonFactory.newStringFieldDefinition(
                "created");

        /**
         * JSON field definition for the modification timestamp.
         */
        public static final JsonFieldDefinition<String> MODIFIED = JsonFactory.newStringFieldDefinition(
                "modified");

        /**
         * JSON field definition for the security configuration (single value).
         */
        public static final JsonFieldDefinition<String> SECURITY = JsonFactory.newStringFieldDefinition(
                "security");

        /**
         * JSON field definition for the security configurations (multiple values).
         */
        public static final JsonFieldDefinition<JsonArray> SECURITY_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "security");

        /**
         * JSON field definition for the profile (single value).
         */
        public static final JsonFieldDefinition<String> PROFILE = JsonFactory.newStringFieldDefinition(
                "profile");

        /**
         * JSON field definition for the profiles (multiple values).
         */
        public static final JsonFieldDefinition<JsonArray> PROFILE_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "profile");

        private JsonFields() {
            throw new AssertionError();
        }

    }
}
