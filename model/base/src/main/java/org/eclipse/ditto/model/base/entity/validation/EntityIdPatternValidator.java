/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.base.entity.validation;

import static org.eclipse.ditto.model.base.entity.id.RegexPatterns.ID_PATTERN;
import static org.eclipse.ditto.model.base.entity.id.RegexPatterns.ID_PATTERN_MESSAGE;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.id.RegexPatterns;

@Immutable
public final class EntityIdPatternValidator extends AbstractPatternValidator {

    public static EntityIdPatternValidator getInstance(final CharSequence id) {
        return new EntityIdPatternValidator(id);
    }

    EntityIdPatternValidator(final CharSequence id) {
        super(id, RegexPatterns.PatternWithMessage.of(ID_PATTERN, ID_PATTERN_MESSAGE));
    }
}
