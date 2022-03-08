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

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Abstract implementation of {@link Interaction.Builder}.
 */
abstract class AbstractInteractionBuilder<
        B extends Interaction.Builder<B, I, E, F>,
        I extends Interaction<I, E, F>,
        E extends FormElement<E>,
        F extends Forms<E>
        >
        extends AbstractTypedJsonObjectBuilder<B, I>
        implements Interaction.Builder<B, I, E, F> {

    protected AbstractInteractionBuilder(final JsonObjectBuilder wrappedObjectBuilder, final Class<?> selfType) {
        super(wrappedObjectBuilder, selfType);
    }

    @Override
    public B setAtType(@Nullable final AtType atType) {
        if (atType != null) {
            if (atType instanceof MultipleAtType) {
                putValue(Interaction.InteractionJsonFields.AT_TYPE_MULTIPLE, ((MultipleAtType) atType).toJson());
            } else if (atType instanceof SingleAtType) {
                putValue(Interaction.InteractionJsonFields.AT_TYPE, atType.toString());
            } else {
                throw new IllegalArgumentException("Unsupported @type: " + atType.getClass().getSimpleName());
            }
        } else {
            remove(Interaction.InteractionJsonFields.AT_TYPE);
        }
        return myself;
    }

    @Override
    public B setTitle(@Nullable final Title title) {
        if (title != null) {
            putValue(Interaction.InteractionJsonFields.TITLE, title.toString());
        } else {
            remove(Interaction.InteractionJsonFields.TITLE);
        }
        return myself;
    }

    @Override
    public B setTitles(@Nullable final Titles titles) {
        if (titles != null) {
            putValue(Interaction.InteractionJsonFields.TITLES, titles.toJson());
        } else {
            remove(Interaction.InteractionJsonFields.TITLES);
        }
        return myself;
    }

    @Override
    public B setDescription(@Nullable final Description description) {
        if (description != null) {
            putValue(Interaction.InteractionJsonFields.DESCRIPTION, description.toString());
        } else {
            remove(Interaction.InteractionJsonFields.DESCRIPTION);
        }
        return myself;
    }

    @Override
    public B setDescriptions(@Nullable final Descriptions descriptions) {
        if (descriptions != null) {
            putValue(Interaction.InteractionJsonFields.DESCRIPTIONS, descriptions.toJson());
        } else {
            remove(Interaction.InteractionJsonFields.DESCRIPTIONS);
        }
        return myself;
    }

    @Override
    public B setForms(@Nullable final F forms) {
        if (forms != null) {
            putValue(Interaction.InteractionJsonFields.FORMS, forms.toJson());
        } else {
            remove(Interaction.InteractionJsonFields.FORMS);
        }
        return myself;
    }

    @Override
    public B setUriVariables(@Nullable final UriVariables uriVariables) {
        if (uriVariables != null) {
            putValue(Interaction.InteractionJsonFields.URI_VARIABLES, uriVariables.toJson());
        } else {
            remove(Interaction.InteractionJsonFields.URI_VARIABLES);
        }
        return myself;
    }

}
