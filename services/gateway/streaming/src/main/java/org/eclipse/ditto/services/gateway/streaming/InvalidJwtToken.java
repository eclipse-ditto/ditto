/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.services.gateway.streaming;

import javax.annotation.Nullable;

/**
 * Simple event which signals that a send JWT was invalid.
 */
public class InvalidJwtToken {

    private final String connectionCorrelationId;
    @Nullable private final String reasonForInvalidity;

    public InvalidJwtToken(final String connectionCorrelationId, @Nullable final String reasonForInvalidity) {
        this.reasonForInvalidity = reasonForInvalidity;
        this.connectionCorrelationId = connectionCorrelationId;
    }

    public String getConnectionCorrelationId() {
        return connectionCorrelationId;
    }

    @Nullable
    public String getReasonForInvalidity() {
        return reasonForInvalidity;
    }
}
