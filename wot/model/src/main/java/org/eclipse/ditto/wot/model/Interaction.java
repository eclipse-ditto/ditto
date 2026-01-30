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

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * An Interaction describes how to interact with a Thing.
 * <p>
 * W3C WoT defines three types of Interaction Affordances: {@link Property}s, {@link Action}s, and {@link Event}s.
 * The InteractionAffordance is the base class that provides common metadata for all interaction types.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#interactionaffordance">WoT TD InteractionAffordance</a>
 * @param <I> the type of the Interaction.
 * @param <E> the type of the Interaction's Form's FormElements.
 * @param <F> the type of the Interaction's Forms.
 * @since 2.4.0
 */
public interface Interaction<I extends Interaction<I, E, F>, E extends FormElement<E>, F extends Forms<E>>
        extends TypedJsonObject<I>, Jsonifiable<JsonObject> {

    /**
     * Returns the optional JSON-LD {@code @type} providing semantic annotations for this interaction.
     *
     * @return the optional semantic type annotation.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#interactionaffordance">WoT TD InteractionAffordance (@type)</a>
     */
    Optional<AtType> getAtType();

    /**
     * Returns the optional human-readable description of this interaction, based on the default language.
     *
     * @return the optional description.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#interactionaffordance">WoT TD InteractionAffordance (description)</a>
     */
    Optional<Description> getDescription();

    /**
     * Returns the optional multi-language map of human-readable descriptions for this interaction.
     *
     * @return the optional multi-language descriptions.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#multilanguage">WoT TD MultiLanguage</a>
     */
    Optional<Descriptions> getDescriptions();

    /**
     * Returns the optional human-readable title of this interaction, based on the default language.
     *
     * @return the optional title.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#interactionaffordance">WoT TD InteractionAffordance (title)</a>
     */
    Optional<Title> getTitle();

    /**
     * Returns the optional multi-language map of human-readable titles for this interaction.
     *
     * @return the optional multi-language titles.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#multilanguage">WoT TD MultiLanguage</a>
     */
    Optional<Titles> getTitles();

    /**
     * Returns the optional collection of Form elements describing how to perform operations on this interaction.
     *
     * @return the optional forms.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#form">WoT TD Form</a>
     */
    Optional<F> getForms();

    /**
     * Returns the optional URI template variables that can be used within the Forms of this interaction.
     *
     * @return the optional URI variables.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#interactionaffordance">WoT TD InteractionAffordance (uriVariables)</a>
     */
    Optional<UriVariables> getUriVariables();

    /**
     * A mutable builder for creating {@link Interaction} instances.
     *
     * @param <B> the type of the Builder.
     * @param <I> the type of the Interaction.
     * @param <E> the type of the FormElements.
     * @param <F> the type of the Forms.
     */
    interface Builder<B extends Builder<B, I, E, F>, I extends Interaction<I, E, F>, E extends FormElement<E>, F extends Forms<E>>
            extends TypedJsonObjectBuilder<B, I> {

        /**
         * Sets the JSON-LD {@code @type} for semantic annotations.
         *
         * @param atType the semantic type, or {@code null} to remove.
         * @return this builder.
         */
        B setAtType(@Nullable AtType atType);

        /**
         * Sets the human-readable title.
         *
         * @param title the title, or {@code null} to remove.
         * @return this builder.
         */
        B setTitle(@Nullable Title title);

        /**
         * Sets the multi-language titles.
         *
         * @param titles the titles map, or {@code null} to remove.
         * @return this builder.
         */
        B setTitles(@Nullable Titles titles);

        /**
         * Sets the human-readable description.
         *
         * @param description the description, or {@code null} to remove.
         * @return this builder.
         */
        B setDescription(@Nullable Description description);

        /**
         * Sets the multi-language descriptions.
         *
         * @param descriptions the descriptions map, or {@code null} to remove.
         * @return this builder.
         */
        B setDescriptions(@Nullable Descriptions descriptions);

        /**
         * Sets the collection of Form elements.
         *
         * @param forms the forms, or {@code null} to remove.
         * @return this builder.
         */
        B setForms(@Nullable F forms);

        /**
         * Sets the URI template variables.
         *
         * @param uriVariables the URI variables, or {@code null} to remove.
         * @return this builder.
         */
        B setUriVariables(@Nullable UriVariables uriVariables);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of an Interaction.
     */
    @Immutable
    final class InteractionJsonFields {

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
         * JSON field definition for the form elements.
         */
        public static final JsonFieldDefinition<JsonArray> FORMS = JsonFactory.newJsonArrayFieldDefinition(
                "forms");

        /**
         * JSON field definition for the URI template variables.
         */
        public static final JsonFieldDefinition<JsonObject> URI_VARIABLES = JsonFactory.newJsonObjectFieldDefinition(
                "uriVariables");

        private InteractionJsonFields() {
            throw new AssertionError();
        }
    }
}
