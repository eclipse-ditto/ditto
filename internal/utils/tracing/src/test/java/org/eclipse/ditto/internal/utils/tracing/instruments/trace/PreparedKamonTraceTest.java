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
package org.eclipse.ditto.internal.utils.tracing.instruments.trace;

import org.junit.Before;

import kamon.context.Context;

public class PreparedKamonTraceTest extends TaggedMetricsInstrumentTest<PreparedTrace> {

    @Before
    public void setup() {
        underTest = new PreparedKamonTrace(Context.Empty(), "prepared");
    }

}
