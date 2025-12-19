/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
 * Mutable builder for {@link FormElementExpectedResponse}s.
 */
final class MutableFormElementExpectedResponseBuilder extends
        AbstractTypedJsonObjectBuilder<FormElementExpectedResponse.Builder, FormElementExpectedResponse>
        implements FormElementExpectedResponse.Builder {

    MutableFormElementExpectedResponseBuilder(final JsonObjectBuilder wrappedObjectBuilder) {
        super(wrappedObjectBuilder, FormElementExpectedResponse.Builder.class);
    }

    @Override
    public FormElementExpectedResponse.Builder setContentType(@Nullable final String contentType) {
        putValue(FormElementExpectedResponse.JsonFields.CONTENT_TYPE, contentType);
        return this;
    }

    @Override
    public FormElementExpectedResponse build() {
        return new ImmutableFormElementExpectedResponse(wrappedObjectBuilder.build());
    }

}
