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
package org.eclipse.ditto.base.model.entity.id.restriction;

/**
 * Base class for tests related to length restrictions of identifiers.
 */
public abstract class LengthRestrictionTestBase {

    protected static final int MAX_LENGTH = 256;

    public static String generateStringExceedingMaxLength() {
        return generateStringExceedingMaxLength("");
    }

    public static String generateStringExceedingMaxLength(final String prefix) {
        return generateStringWithLength(MAX_LENGTH + 1, prefix);
    }

    public static String generateStringWithMaxLength() {
        return generateStringWithMaxLength("");
    }

    public static String generateStringWithMaxLength(final String prefix) {
        return generateStringWithLength(MAX_LENGTH, prefix);
    }

    public static String generateStringWithLength(final int length) {
        return generateStringWithLength(length, "");
    }

    public static String generateStringWithLength(final int length, final String prefix) {
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(prefix);
        for (int i = 0; i < (length - prefix.length()); i++) {
            stringBuilder.append("a");
        }
        return stringBuilder.toString();
    }

}
