/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.protocol;

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

    /**
     * This constructor is the obligation of all subclasses of {@code ProtocolAdapterProvider}.
     *
     * @param protocolConfigReader the argument.
     */
    public DittoProtocolAdapterProvider(final ProtocolConfigReader protocolConfigReader) {
        super(protocolConfigReader);
    }

    @Override
    public ProtocolAdapter createProtocolAdapter() {
        return DittoProtocolAdapter.newInstance();
    }

    @Override
    public ProtocolAdapter createProtocolAdapterForCompatibilityMode() {
        final HeaderTranslator compatibleHeaderTranslator = DittoProtocolAdapter.headerTranslator()
                .forgetHeaderKeys(protocolConfigReader().incompatibleBlacklist());
        return DittoProtocolAdapter.of(compatibleHeaderTranslator);
    }

    @Override
    public HeaderTranslator createHttpHeaderTranslator() {
        final HeaderDefinition[] blacklist = protocolConfigReader().blacklist()
                .stream()
                .map(Ignored::new)
                .toArray(HeaderDefinition[]::new);
        return HeaderTranslator.of(DittoHeaderDefinition.values(), MessageHeaderDefinition.values(), blacklist);
    }
}
