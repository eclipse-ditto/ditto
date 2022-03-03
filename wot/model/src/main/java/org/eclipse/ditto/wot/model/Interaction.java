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
 * W3C WoT defines three types of Interaction Affordances: {@link Property}s, {@link Action}s, and {@link Event}s.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#interactionaffordance">WoT TD InteractionAffordance</a>
 * @param <I> the type of the Interaction.
 * @param <F> the type of the Interaction's Forms.
 * @param <E> the type of the Interaction's Form's FormElements.
 * @since 2.4.0
 */
public interface Interaction<I extends Interaction<I, E, F>, E extends FormElement<E>, F extends Forms<E>>
        extends TypedJsonObject<I>, Jsonifiable<JsonObject> {

    Optional<AtType> getAtType();

    Optional<Description> getDescription();

    Optional<Descriptions> getDescriptions();

    Optional<Title> getTitle();

    Optional<Titles> getTitles();

    Optional<F> getForms();

    Optional<UriVariables> getUriVariables();

    interface Builder<B extends Builder<B, I, E, F>, I extends Interaction<I, E, F>, E extends FormElement<E>, F extends Forms<E>>
            extends TypedJsonObjectBuilder<B, I> {

        B setAtType(@Nullable AtType atType);

        B setTitle(@Nullable Title title);

        B setTitles(@Nullable Titles titles);

        B setDescription(@Nullable Description description);

        B setDescriptions(@Nullable Descriptions descriptions);

        B setForms(@Nullable F forms);

        B setUriVariables(@Nullable UriVariables uriVariables);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of an Interaction.
     */
    @Immutable
    final class InteractionJsonFields {

        public static final JsonFieldDefinition<String> AT_TYPE = JsonFactory.newStringFieldDefinition(
                "@type");

        public static final JsonFieldDefinition<JsonArray> AT_TYPE_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "@type");

        public static final JsonFieldDefinition<String> TITLE = JsonFactory.newStringFieldDefinition(
                "title");

        public static final JsonFieldDefinition<JsonObject> TITLES = JsonFactory.newJsonObjectFieldDefinition(
                "titles");

        public static final JsonFieldDefinition<String> DESCRIPTION = JsonFactory.newStringFieldDefinition(
                "description");

        public static final JsonFieldDefinition<JsonObject> DESCRIPTIONS = JsonFactory.newJsonObjectFieldDefinition(
                "descriptions");

        public static final JsonFieldDefinition<JsonArray> FORMS = JsonFactory.newJsonArrayFieldDefinition(
                "forms");

        public static final JsonFieldDefinition<JsonObject> URI_VARIABLES = JsonFactory.newJsonObjectFieldDefinition(
                "uriVariables");

        private InteractionJsonFields() {
            throw new AssertionError();
        }
    }
}
