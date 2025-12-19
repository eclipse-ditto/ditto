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
 * Mutable builder for {@link FormElementAdditionalResponse}s.
 */
final class MutableFormElementAdditionalResponseBuilder extends
        AbstractTypedJsonObjectBuilder<FormElementAdditionalResponse.Builder, FormElementAdditionalResponse>
        implements FormElementAdditionalResponse.Builder {

    MutableFormElementAdditionalResponseBuilder(final JsonObjectBuilder wrappedObjectBuilder) {
        super(wrappedObjectBuilder, FormElementAdditionalResponse.Builder.class);
    }

    @Override
    public FormElementAdditionalResponse.Builder setSuccess(@Nullable final Boolean success) {
        if (success != null) {
            putValue(FormElementAdditionalResponse.JsonFields.SUCCESS, success);
        } else {
            wrappedObjectBuilder.remove(FormElementAdditionalResponse.JsonFields.SUCCESS);
        }
        return this;
    }

    @Override
    public FormElementAdditionalResponse.Builder setContentType(@Nullable final String contentType) {
        putValue(FormElementAdditionalResponse.JsonFields.CONTENT_TYPE, contentType);
        return this;
    }

    @Override
    public FormElementAdditionalResponse.Builder setSchema(@Nullable final String schema) {
        putValue(FormElementAdditionalResponse.JsonFields.SCHEMA, schema);
        return this;
    }

    @Override
    public FormElementAdditionalResponse build() {
        return new ImmutableFormElementAdditionalResponse(wrappedObjectBuilder.build());
    }

}
