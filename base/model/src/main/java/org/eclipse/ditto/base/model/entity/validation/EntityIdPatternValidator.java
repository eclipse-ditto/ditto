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
package org.eclipse.ditto.base.model.entity.validation;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.RegexPatterns;

/**
 * Validator capable of validating {@code Entity} identifiers via pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#ID_PATTERN}.
 *
 * @since 1.2.0
 */
@Immutable
public final class EntityIdPatternValidator extends AbstractPatternValidator {

    /**
     * @param id the char sequence that is validated
     * @return new instance of {@link org.eclipse.ditto.base.model.entity.validation.EntityIdPatternValidator}
     */
    public static EntityIdPatternValidator getInstance(final CharSequence id) {
        return new EntityIdPatternValidator(id);
    }

    EntityIdPatternValidator(final CharSequence id) {
        super(id, RegexPatterns.ID_PATTERN, "The given identifier is not valid.");
    }
}
