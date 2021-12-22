/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.models.signal;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;

/**
 * Helpers for restoring command headers, e.g. for connectivity.
 */
public final class CommandHeaderRestoration {

    private CommandHeaderRestoration() {
        throw new AssertionError();
    }

    /**
     * Restores the connectivity relevant headers for commands and/or command responses.
     *
     * @param signal the signal to adjust the headers in.
     * @param headersToRestoreFrom the original headers to restore connectivity headers from.
     * @param <T> the type of the DittoHeadersSettable to pass through.
     * @return the potentially adjusted signal with restored connectivity headers.
     */
    @SuppressWarnings("unchecked")
    public static <T extends DittoHeadersSettable<?>> T restoreCommandConnectivityHeaders(
            final T signal,
            final DittoHeaders headersToRestoreFrom) {

        final var signalDittoHeaders = signal.getDittoHeaders();
        final var enhancedHeadersBuilder = signalDittoHeaders.toBuilder()
                .removeHeader(DittoHeaderDefinition.EXPECTED_RESPONSE_TYPES.getKey())
                .removeHeader(DittoHeaderDefinition.INBOUND_PAYLOAD_MAPPER.getKey())
                .removeHeader(DittoHeaderDefinition.REPLY_TARGET.getKey());
        if (headersToRestoreFrom.containsKey(DittoHeaderDefinition.EXPECTED_RESPONSE_TYPES.getKey())) {
            enhancedHeadersBuilder.expectedResponseTypes(headersToRestoreFrom.getExpectedResponseTypes());
        }
        headersToRestoreFrom.getInboundPayloadMapper().ifPresent(enhancedHeadersBuilder::inboundPayloadMapper);
        headersToRestoreFrom.getReplyTarget().ifPresent(enhancedHeadersBuilder::replyTarget);

        return (T) signal.setDittoHeaders(enhancedHeadersBuilder.build());
    }

}
