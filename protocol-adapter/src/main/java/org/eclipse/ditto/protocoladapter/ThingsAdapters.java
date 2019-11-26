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
 * Instantiates {@link Adapter}s used to process thing commands, responses, messages, events and errors.
 */
class ThingsAdapters extends AbstractAdapterResolver {

    ThingsAdapters(final ErrorRegistry<DittoRuntimeException> errorRegistry, final HeaderTranslator headerTranslator) {
        super(
                ThingQueryCommandAdapter.of(headerTranslator),
                ThingQueryCommandResponseAdapter.of(headerTranslator),
                ThingModifyCommandAdapter.of(headerTranslator),
                ThingModifyCommandResponseAdapter.of(headerTranslator),
                MessageCommandAdapter.of(headerTranslator),
                MessageCommandResponseAdapter.of(headerTranslator),
                ThingEventAdapter.of(headerTranslator),
                ThingErrorResponseAdapter.of(headerTranslator, errorRegistry));
    }
}