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
package org.eclipse.ditto.internal.utils.protocol;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.HeaderDefinition;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.internal.utils.protocol.config.ProtocolConfig;
import org.eclipse.ditto.messages.model.MessageHeaderDefinition;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;

/**
 * Provider of Ditto protocol adapter.
 */
public final class DittoProtocolAdapterProvider extends ProtocolAdapterProvider {

    private final ProtocolAdapter dittoProtocolAdapter;

    /**
     * This constructor is the obligation of all subclasses of {@code ProtocolAdapterProvider}.
     *
     * @param protocolConfig provides the class name of the ProtocolAdapterProvider to be loaded.
     */
    public DittoProtocolAdapterProvider(final ProtocolConfig protocolConfig) {
        super(protocolConfig);
        dittoProtocolAdapter = DittoProtocolAdapter.newInstance();
    }

    @Override
    public ProtocolAdapter getProtocolAdapter(@Nullable final String userAgent) {
        return dittoProtocolAdapter;
    }

    @Override
    protected HeaderTranslator createHttpHeaderTranslatorInstance(final ProtocolConfig protocolConfig) {
        final HeaderDefinition[] blocklist = protocolConfig.getBlockedHeaderKeys()
                .stream()
                .map(Ignored::new)
                .toArray(HeaderDefinition[]::new);

        return HeaderTranslator.of(DittoHeaderDefinition.values(), MessageHeaderDefinition.values(), blocklist);
    }

}
