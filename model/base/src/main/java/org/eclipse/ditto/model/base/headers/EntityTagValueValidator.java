/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.base.headers;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;

/**
 * This validator checks if a CharSequence is valid according to {@link EntityTag#isValid(CharSequence)}.
 * If validation fails, a {@link DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class EntityTagValueValidator extends AbstractHeaderValueValidator {

    /**
     * URL to RFC describing ETag.
     */
    static final String RFC_7232_SECTION_2_3 = "https://tools.ietf.org/html/rfc7232#section-2.3";

    private EntityTagValueValidator() {
        super(EntityTag.class::equals);
    }

    static EntityTagValueValidator getInstance() {
        return new EntityTagValueValidator();
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        if (!EntityTag.isValid(value)) {
            throw DittoHeaderInvalidException
                    .newInvalidTypeBuilder(definition, value, "entity-tag")
                    .href(RFC_7232_SECTION_2_3)
                    .build();
        }
    }

}
