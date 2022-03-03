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
 * Mutable builder for {@link IconLink}s.
 */
final class MutableIconLinkBuilder extends AbstractBaseLinkBuilder<IconLink.Builder, IconLink>
        implements IconLink.Builder {

    MutableIconLinkBuilder(final JsonObjectBuilder wrappedObjectBuilder) {
        super(wrappedObjectBuilder, MutableIconLinkBuilder.class);
    }

    @Override
    public IconLink build() {
        return new ImmutableIconLink(wrappedObjectBuilder.build());
    }

    @Override
    public IconLink.Builder setSizes(@Nullable final String sizes) {
        putValue(IconLink.JsonFields.SIZES, sizes);
        return myself;
    }

}
