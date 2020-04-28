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
package org.eclipse.ditto.protocoladapter.provider;

import org.eclipse.ditto.protocoladapter.Adapter;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;

/**
 * Provider for all Acknowledgement adapters.
 */
public interface AcknowledgementAdapterProvider extends AdapterProvider {

    /**
     * @return the acknowledgement adapter.
     */
    Adapter<Acknowledgement> getAcknowledgementAdapter();

    /**
     * @return the acknowledgements adapter.
     */
    Adapter<Acknowledgements> getAcknowledgementsAdapter();
}
