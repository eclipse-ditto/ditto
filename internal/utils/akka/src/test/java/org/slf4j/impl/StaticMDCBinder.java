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
package org.slf4j.impl;

import javax.annotation.concurrent.Immutable;

import org.slf4j.spi.MDCAdapter;

/**
 * The purpose of this class is to use {@link ObservableMdcAdapter} as MDC adapter for logging.
 *
 * @since 1.3.0
 */
@Immutable
public final class StaticMDCBinder {

    private static final StaticMDCBinder INSTANCE = new StaticMDCBinder();

    private StaticMDCBinder() {
        super();
    }

    public static StaticMDCBinder getSingleton() {
        return INSTANCE;
    }

    public MDCAdapter getMDCA() {
        return ObservableMdcAdapter.getInstance();
    }

    public String getMDCAdapterClassStr() {
        return ObservableMdcAdapter.class.getName();
    }

}
