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
 * Abstract implementation of {@link BaseLink.Builder}.
 */
abstract class AbstractBaseLinkBuilder<B extends BaseLink.Builder<B, L>, L extends BaseLink<L>>
        extends AbstractTypedJsonObjectBuilder<B, L>
        implements BaseLink.Builder<B, L> {

    protected AbstractBaseLinkBuilder(final JsonObjectBuilder wrappedObjectBuilder, final Class<?> selfType) {
        super(wrappedObjectBuilder, selfType);
    }

    @Override
    public B setHref(@Nullable final IRI href) {
        if (href != null) {
            putValue(BaseLink.BaseLinkJsonFields.HREF, href.toString());
        } else {
            remove(BaseLink.BaseLinkJsonFields.HREF);
        }
        return myself;
    }

    @Override
    public B setType(@Nullable final String type) {
        putValue(BaseLink.BaseLinkJsonFields.TYPE, type);
        return myself;
    }

    @Override
    public B setRel(@Nullable final String rel) {
        putValue(BaseLink.BaseLinkJsonFields.REL, rel);
        return myself;
    }

    @Override
    public B setAnchor(@Nullable final IRI anchor) {
        if (anchor != null) {
            putValue(BaseLink.BaseLinkJsonFields.ANCHOR, anchor.toString());
        } else {
            remove(BaseLink.BaseLinkJsonFields.ANCHOR);
        }
        return myself;
    }

    @Override
    public B setHreflang(@Nullable final Hreflang hreflang) {
        if (hreflang != null) {
            if (hreflang instanceof MultipleHreflang) {
                putValue(BaseLink.BaseLinkJsonFields.HREFLANG_MULTIPLE, ((MultipleHreflang) hreflang).toJson());
            } else if (hreflang instanceof SingleHreflang) {
                putValue(BaseLink.BaseLinkJsonFields.HREFLANG, hreflang.toString());
            } else {
                throw new IllegalArgumentException("Unsupported hreflang: " + hreflang.getClass().getSimpleName());
            }
        } else {
            remove(BaseLink.BaseLinkJsonFields.HREFLANG);
        }
        return myself;
    }
}
