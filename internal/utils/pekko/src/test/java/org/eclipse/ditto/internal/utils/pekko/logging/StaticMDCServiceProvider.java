/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pekko.logging;

import javax.annotation.concurrent.Immutable;

import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.helpers.NOPLoggerFactory;
import org.slf4j.helpers.NOP_FallbackServiceProvider;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

/**
 * The purpose of this class is to use {@link ObservableMdcAdapter} as MDC adapter for logging.
 *
 * @since 1.3.0
 */
@Immutable
public final class StaticMDCServiceProvider implements SLF4JServiceProvider {

    private final ILoggerFactory loggerFactory = new NOPLoggerFactory();
    private final IMarkerFactory markerFactory = new BasicMarkerFactory();

    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggerFactory;
    }

    @Override
    public IMarkerFactory getMarkerFactory() {
        return markerFactory;
    }

    @Override
    public MDCAdapter getMDCAdapter() {
        return ObservableMdcAdapter.getInstance();
    }

    @Override
    public String getRequestedApiVersion() {
        return NOP_FallbackServiceProvider.REQUESTED_API_VERSION;
    }

    @Override
    public void initialize() {
        // already initialized
    }
}
