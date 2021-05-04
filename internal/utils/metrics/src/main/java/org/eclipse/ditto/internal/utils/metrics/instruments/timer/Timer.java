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
package org.eclipse.ditto.internal.utils.metrics.instruments.timer;

import org.eclipse.ditto.internal.utils.metrics.instruments.MetricInstrument;

/**
 * A Timer metric measures the duration of something.
 */
public interface Timer extends MetricInstrument {

    /**
     * Gets the name of this timer.
     *
     * @return the name of this timer.
     */
    String getName();
}
