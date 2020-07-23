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
package org.eclipse.ditto.model.base.entity.id.restriction;

import org.eclipse.ditto.model.base.entity.validation.AbstractPatternValidator;

/**
 * Base class for tests related to length restrictions of identifiers.
 */
public abstract class LengthRestrictionTestBase {

    public static final int MAX_LENGTH = AbstractPatternValidator.MAX_LENGTH;

    public String generateStringExceedingMaxLength() {
        return generateStringExceedingMaxLength("");
    }

    public String generateStringExceedingMaxLength(final String prefix) {
        return generateStringWithLength(MAX_LENGTH + 1, prefix);
    }

    public String generateStringWithMaxLength() {
        return generateStringWithMaxLength("");
    }

    public String generateStringWithMaxLength(final String prefix) {
        return generateStringWithLength(AbstractPatternValidator.MAX_LENGTH, prefix);
    }

    public String generateStringWithLength(final int length) {
        return generateStringWithLength(length, "");
    }

    public String generateStringWithLength(final int length, final String prefix) {
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(prefix);
        for (int i = 0; i < (length - prefix.length()); i++) {
            stringBuilder.append("a");
        }
        return stringBuilder.toString();
    }

}
