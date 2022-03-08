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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link FormElementSubprotocol}.
 */
@Immutable
final class ImmutableFormElementSubprotocol implements FormElementSubprotocol {

    private final String subprotocol;

    ImmutableFormElementSubprotocol(final CharSequence subprotocol) {
        this.subprotocol = checkNotNull(subprotocol, "subprotocol").toString();
    }

    @Override
    public int length() {
        return subprotocol.length();
    }

    @Override
    public char charAt(final int index) {
        return subprotocol.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return subprotocol.subSequence(start, end);
    }

    @Override
    public String toString() {
        return subprotocol;
    }
}
