/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.protocol;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.HeaderDefinition;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;

/**
 * Provider of Ditto protocol adapter.
 */
public final class DittoProtocolAdapterProvider extends ProtocolAdapterProvider {

    private DittoProtocolAdapter dittoProtocolAdapter;
    private HeaderTranslator headerTranslator;

    /**
     * This constructor is the obligation of all subclasses of {@code ProtocolAdapterProvider}.
     *
     * @param protocolConfigReader the argument.
     */
    public DittoProtocolAdapterProvider(final ProtocolConfigReader protocolConfigReader) {
        super(protocolConfigReader);
        dittoProtocolAdapter = DittoProtocolAdapter.newInstance();
        headerTranslator = createHeaderTranslator(protocolConfigReader);
    }

    @Override
    public ProtocolAdapter getProtocolAdapter(@Nullable final String userAgent) {
        return dittoProtocolAdapter;
    }

    @Override
    public HeaderTranslator getHttpHeaderTranslator() {
        return headerTranslator;
    }

    private static HeaderTranslator createHeaderTranslator(final ProtocolConfigReader protocolConfigReader) {
        final HeaderDefinition[] blacklist = protocolConfigReader.blacklist()
                .stream()
                .map(Ignored::new)
                .toArray(HeaderDefinition[]::new);
        return HeaderTranslator.of(DittoHeaderDefinition.values(), MessageHeaderDefinition.values(), blacklist);
    }
}
