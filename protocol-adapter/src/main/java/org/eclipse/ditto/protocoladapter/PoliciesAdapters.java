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
package org.eclipse.ditto.protocoladapter;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.signals.base.ErrorRegistry;

/**
 * Instantiates {@link Adapter}s used to process policy commands, responses and errors.
 */
class PoliciesAdapters extends AbstractAdapterResolver {

    PoliciesAdapters(final ErrorRegistry<DittoRuntimeException> errorRegistry,
            final HeaderTranslator headerTranslator) {
        super(PolicyQueryCommandAdapter.of(headerTranslator),
                PolicyQueryCommandResponseAdapter.of(headerTranslator),
                PolicyModifyCommandAdapter.of(headerTranslator),
                PolicyModifyCommandResponseAdapter.of(headerTranslator),
                null,
                null,
                null,
                PolicyErrorResponseAdapter.of(headerTranslator, errorRegistry)
        );
    }
}