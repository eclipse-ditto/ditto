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

import org.eclipse.ditto.json.JsonObject;

/**
 * Abstract implementation of {@link Interaction}.
 */
abstract class AbstractInteraction<I extends Interaction<I, E, F>, E extends FormElement<E>, F extends Forms<E>>
        extends AbstractTypedJsonObject<I>
        implements Interaction<I, E, F> {

    protected AbstractInteraction(final JsonObject wrappedObject) {
        super(wrappedObject);
    }

    @Override
    public JsonObject toJson() {
        return wrappedObject;
    }

    @Override
    public Optional<AtType> getAtType() {
        return Optional.ofNullable(
                TdHelpers.getValueIgnoringWrongType(wrappedObject, InteractionJsonFields.AT_TYPE_MULTIPLE)
                        .map(MultipleAtType::fromJson)
                        .map(AtType.class::cast)
                        .orElseGet(() -> wrappedObject.getValue(InteractionJsonFields.AT_TYPE)
                                .map(SingleAtType::of)
                                .orElse(null))
        );
    }

    @Override
    public Optional<Description> getDescription() {
        return wrappedObject.getValue(InteractionJsonFields.DESCRIPTION)
                .map(Description::of);
    }

    @Override
    public Optional<Descriptions> getDescriptions() {
        return wrappedObject.getValue(InteractionJsonFields.DESCRIPTIONS)
                .map(Descriptions::fromJson);
    }

    @Override
    public Optional<Title> getTitle() {
        return wrappedObject.getValue(InteractionJsonFields.TITLE)
                .map(Title::of);
    }

    @Override
    public Optional<Titles> getTitles() {
        return wrappedObject.getValue(InteractionJsonFields.TITLES)
                .map(Titles::fromJson);
    }

    @Override
    public Optional<UriVariables> getUriVariables() {
        return wrappedObject.getValue(InteractionJsonFields.URI_VARIABLES)
                .map(UriVariables::fromJson);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractInteraction;
    }

}
