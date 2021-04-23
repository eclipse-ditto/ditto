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
package org.eclipse.ditto.connectivity.model;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.validation.AbstractPatternValidator;

/**
 * Validator capable of validating {@link ConnectionId connection IDs} via pattern {@link ConnectionId#ID_PATTERN}.
 *
 * @since 1.4.0
 */
@Immutable
public final class ConnectionIdPatternValidator extends AbstractPatternValidator {

    /**
     * @param id the char sequence that is validated
     * @return new instance of {@link ConnectionIdPatternValidator}
     */
    public static ConnectionIdPatternValidator getInstance(final CharSequence id) {
        return new ConnectionIdPatternValidator(id);
    }

    ConnectionIdPatternValidator(final CharSequence id) {
        super(id, ConnectionId.ID_PATTERN, "The given identifier is not valid.");
    }
}
