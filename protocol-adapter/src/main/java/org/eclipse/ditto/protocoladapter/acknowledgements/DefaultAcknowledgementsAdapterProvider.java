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
package org.eclipse.ditto.protocoladapter.acknowledgements;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.protocoladapter.Adapter;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.provider.AcknowledgementAdapterProvider;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.signals.base.ErrorRegistry;

/**
 * Instantiates and provides {@link Adapter}s used to process Acknowledgements.
 */
public final class DefaultAcknowledgementsAdapterProvider implements AcknowledgementAdapterProvider {

    private final AcknowledgementAdapter acknowledgementAdapter;
    private final AcknowledgementsAdapter acknowledgementsAdapter;

    public DefaultAcknowledgementsAdapterProvider(final ErrorRegistry<DittoRuntimeException> errorRegistry,
            final HeaderTranslator headerTranslator) {
        this.acknowledgementAdapter = AcknowledgementAdapter.getInstance(headerTranslator);
        this.acknowledgementsAdapter = AcknowledgementsAdapter.getInstance(headerTranslator);
    }

    @Override
    public List<Adapter<?>> getAdapters() {
        return Arrays.asList(acknowledgementAdapter, acknowledgementsAdapter);
    }

    @Override
    public Adapter<Acknowledgement> getAcknowledgementAdapter() {
        return acknowledgementAdapter;
    }

    @Override
    public Adapter<Acknowledgements> getAcknowledgementsAdapter() {
        return acknowledgementsAdapter;
    }
}
