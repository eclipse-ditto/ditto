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
 * Abstract implementation of {@link BaseLink}.
 */
abstract class AbstractBaseLink<L extends BaseLink<L>>
        extends AbstractTypedJsonObject<L>
        implements BaseLink<L> {

    protected AbstractBaseLink(final JsonObject wrappedObject) {
        super(wrappedObject);
    }

    @Override
    public IRI getHref() {
        return IRI.of(wrappedObject.getValueOrThrow(BaseLinkJsonFields.HREF));
    }

    @Override
    public Optional<String> getType() {
        return wrappedObject.getValue(BaseLinkJsonFields.TYPE);
    }

    @Override
    public Optional<String> getRel() {
        return wrappedObject.getValue(BaseLinkJsonFields.REL);
    }

    @Override
    public Optional<IRI> getAnchor() {
        return wrappedObject.getValue(BaseLinkJsonFields.ANCHOR)
                .map(IRI::of);
    }

    @Override
    public Optional<Hreflang> getHreflang() {
        return Optional.ofNullable(
                TdHelpers.getValueIgnoringWrongType(wrappedObject, BaseLinkJsonFields.HREFLANG_MULTIPLE)
                        .map(MultipleHreflang::fromJson)
                        .map(Hreflang.class::cast)
                        .orElseGet(() -> wrappedObject.getValue(BaseLinkJsonFields.HREFLANG)
                                .map(SingleHreflang::of)
                                .orElse(null)
                        )
        );
    }

    @Override
    public JsonObject toJson() {
        return wrappedObject;
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractBaseLink;
    }

}
