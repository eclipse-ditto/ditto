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
package org.eclipse.ditto.services.utils.metrics.instruments;

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
