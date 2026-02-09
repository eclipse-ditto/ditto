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
import java.util.Collection;

import javax.annotation.Nullable;

/**
 * Contains common state/behavior shared by {@link ThingDescription.Builder} and {@link ThingModel.Builder}.
 *
 * @param <B> the type of the ThingSkeletonBuilder.
 * @param <T> the type of the built ThingSkeleton.
 * @since 2.4.0
 */
public interface ThingSkeletonBuilder<B extends ThingSkeletonBuilder<B, T>, T extends ThingSkeleton<T>> extends
        TypedJsonObjectBuilder<B, T> {

    /**
     * Sets the JSON-LD context.
     *
     * @param atContext the context.
     * @return this builder.
     */
    B setAtContext(AtContext atContext);

    /**
     * Sets the JSON-LD semantic type(s).
     *
     * @param atType the type(s), or {@code null} to remove.
     * @return this builder.
     */
    B setAtType(@Nullable AtType atType);

    /**
     * Sets the identifier of this Thing Description or Thing Model.
     *
     * @param id the identifier IRI, or {@code null} to remove.
     * @return this builder.
     */
    B setId(@Nullable IRI id);

    /**
     * Sets the human-readable title.
     *
     * @param title the title, or {@code null} to remove.
     * @return this builder.
     */
    B setTitle(@Nullable Title title);

    /**
     * Sets the multilingual titles.
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
     * Sets the multilingual descriptions.
     *
     * @param descriptions the descriptions map, or {@code null} to remove.
     * @return this builder.
     */
    B setDescriptions(@Nullable Descriptions descriptions);

    /**
     * Sets the version information.
     *
     * @param version the version, or {@code null} to remove.
     * @return this builder.
     */
    B setVersion(@Nullable Version version);

    /**
     * Sets the base IRI for relative URI references.
     *
     * @param base the base IRI, or {@code null} to remove.
     * @return this builder.
     */
    B setBase(@Nullable IRI base);

    /**
     * Sets the links to related resources.
     *
     * @param links the links, or {@code null} to remove.
     * @return this builder.
     */
    B setLinks(@Nullable Links links);

    /**
     * Sets the property affordances.
     *
     * @param properties the properties, or {@code null} to remove.
     * @return this builder.
     */
    B setProperties(@Nullable Properties properties);

    /**
     * Sets the action affordances.
     *
     * @param actions the actions, or {@code null} to remove.
     * @return this builder.
     */
    B setActions(@Nullable Actions actions);

    /**
     * Sets the event affordances.
     *
     * @param events the events, or {@code null} to remove.
     * @return this builder.
     */
    B setEvents(@Nullable Events events);

    /**
     * Sets the links to related resources from a collection.
     *
     * @param links the collection of links.
     * @return this builder.
     */
    B setLinks(Collection<BaseLink<?>> links);

    /**
     * Sets the root-level form elements from a collection.
     *
     * @param forms the collection of form elements.
     * @return this builder.
     */
    B setForms(Collection<RootFormElement> forms);

    /**
     * Sets the root-level form elements.
     *
     * @param forms the forms, or {@code null} to remove.
     * @return this builder.
     */
    B setForms(@Nullable RootForms forms);

    /**
     * Sets the URI template variables.
     *
     * @param uriVariables the URI variables, or {@code null} to remove.
     * @return this builder.
     */
    B setUriVariables(@Nullable UriVariables uriVariables);

    /**
     * Sets the security scheme definitions.
     *
     * @param securityDefinitions the security definitions, or {@code null} to remove.
     * @return this builder.
     */
    B setSecurityDefinitions(@Nullable SecurityDefinitions securityDefinitions);

    /**
     * Sets the data schema definitions for reuse.
     *
     * @param schemaDefinitions the schema definitions, or {@code null} to remove.
     * @return this builder.
     */
    B setSchemaDefinitions(@Nullable SchemaDefinitions schemaDefinitions);

    /**
     * Sets the support contact URI.
     *
     * @param support the support URI, or {@code null} to remove.
     * @return this builder.
     */
    B setSupport(@Nullable IRI support);

    /**
     * Sets the creation timestamp.
     *
     * @param created the creation timestamp, or {@code null} to remove.
     * @return this builder.
     */
    B setCreated(@Nullable Instant created);

    /**
     * Sets the last modification timestamp.
     *
     * @param modified the modification timestamp, or {@code null} to remove.
     * @return this builder.
     */
    B setModified(@Nullable Instant modified);

    /**
     * Sets the security configuration to apply by default.
     *
     * @param security the security configuration, or {@code null} to remove.
     * @return this builder.
     */
    B setSecurity(@Nullable Security security);

    /**
     * Sets the WoT profile(s) this Thing conforms to.
     *
     * @param profile the profile(s), or {@code null} to remove.
     * @return this builder.
     */
    B setProfile(@Nullable Profile profile);

}
