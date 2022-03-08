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

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;

/**
 * Abstract implementation of {@link ThingSkeleton}.
 */
abstract class AbstractThingSkeleton<T extends ThingSkeleton<T>>
        extends AbstractTypedJsonObject<T>
        implements ThingSkeleton<T> {

    protected AbstractThingSkeleton(final JsonObject wrappedObject) {
        super(wrappedObject);
    }

    @Override
    public AtContext getAtContext() {
        return TdHelpers.getValueIgnoringWrongType(wrappedObject, JsonFields.AT_CONTEXT_MULTIPLE)
                .map(MultipleAtContext::fromJson)
                .map(AtContext.class::cast)
                .orElseGet(() -> SingleUriAtContext.of(getValueOrThrow(JsonFields.AT_CONTEXT)));
    }

    @Override
    public Optional<AtType> getAtType() {
        return Optional.ofNullable(
                TdHelpers.getValueIgnoringWrongType(wrappedObject, JsonFields.AT_TYPE_MULTIPLE)
                        .map(MultipleAtType::fromJson)
                        .map(AtType.class::cast)
                        .orElseGet(() -> getValue(JsonFields.AT_TYPE).map(SingleAtType::of).orElse(null))
        );
    }

    @Override
    public Optional<IRI> getId() {
        return getValue(JsonFields.ID)
                .map(IRI::of);
    }

    @Override
    public Optional<Title> getTitle() {
        return getValue(JsonFields.TITLE)
                .map(Title::of);
    }

    @Override
    public Optional<Titles> getTitles() {
        return getValue(JsonFields.TITLES)
                .map(Titles::fromJson);
    }

    @Override
    public Optional<Description> getDescription() {
        return getValue(JsonFields.DESCRIPTION)
                .map(Description::of);
    }

    @Override
    public Optional<Descriptions> getDescriptions() {
        return getValue(JsonFields.DESCRIPTIONS)
                .map(Descriptions::fromJson);
    }

    @Override
    public Optional<Version> getVersion() {
        return getValue(JsonFields.VERSION)
                .map(Version::fromJson);
    }

    @Override
    public Optional<IRI> getBase() {
        return getValue(JsonFields.BASE)
                .map(IRI::of);
    }

    @Override
    public Optional<Links> getLinks() {
        return getValue(JsonFields.LINKS)
                .map(Links::fromJson);
    }

    @Override
    public Optional<Properties> getProperties() {
        return getValue(JsonFields.PROPERTIES)
                .map(Properties::fromJson);
    }

    @Override
    public Optional<Actions> getActions() {
        return getValue(JsonFields.ACTIONS)
                .map(Actions::fromJson);
    }

    @Override
    public Optional<Events> getEvents() {
        return getValue(JsonFields.EVENTS)
                .map(Events::fromJson);
    }

    @Override
    public Optional<RootForms> getForms() {
        return getValue(JsonFields.FORMS)
                .map(RootForms::fromJson);
    }

    @Override
    public Optional<SecurityDefinitions> getSecurityDefinitions() {
        return getValue(JsonFields.SECURITY_DEFINITIONS)
                .map(SecurityDefinitions::fromJson);
    }

    @Override
    public Optional<SchemaDefinitions> getSchemaDefinitions() {
        return getValue(JsonFields.SCHEMA_DEFINITIONS)
                .map(SchemaDefinitions::fromJson);
    }

    @Override
    public Optional<UriVariables> getUriVariables() {
        return getValue(JsonFields.URI_VARIABLES)
                .map(UriVariables::fromJson);
    }

    @Override
    public Optional<IRI> getSupport() {
        return getValue(JsonFields.SUPPORT)
                .map(IRI::of);
    }

    @Override
    public Optional<Instant> getCreated() {
        return getValue(JsonFields.CREATED)
                .map(Instant::parse);
    }

    @Override
    public Optional<Instant> getModified() {
        return getValue(JsonFields.MODIFIED)
                .map(Instant::parse);
    }

    @Override
    public Optional<Security> getSecurity() {
        return Optional.ofNullable(
                TdHelpers.getValueIgnoringWrongType(wrappedObject, JsonFields.SECURITY_MULTIPLE)
                        .map(MultipleSecurity::fromJson)
                        .map(Security.class::cast)
                        .orElseGet(() -> getValue(JsonFields.SECURITY).map(SingleSecurity::of).orElse(null))
        );
    }

    @Override
    public Optional<Profile> getProfile() {
        return Optional.ofNullable(
                TdHelpers.getValueIgnoringWrongType(wrappedObject, JsonFields.PROFILE_MULTIPLE)
                        .map(MultipleProfile::fromJson)
                        .map(Profile.class::cast)
                        .orElseGet(() -> getValue(JsonFields.PROFILE).map(SingleProfile::of).orElse(null))
        );
    }

    @Override
    public JsonObject toJson() {
        return wrappedObject;
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractThingSkeleton;
    }

    @Override
    public String toString() {
        return "wrappedObject=" + wrappedObject;
    }

}
