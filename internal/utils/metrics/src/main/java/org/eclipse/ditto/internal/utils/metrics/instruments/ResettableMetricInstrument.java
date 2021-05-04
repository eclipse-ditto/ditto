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
package org.eclipse.ditto.internal.utils.metrics.instruments;

/**
 * {@link MetricInstrument} which is able to be reset.
 */
public interface ResettableMetricInstrument extends MetricInstrument {

    /**
     * Resets the metric.
     *
     * @return Returns true if metric could be reset successfully. False if not.
     */
    boolean reset();
}
