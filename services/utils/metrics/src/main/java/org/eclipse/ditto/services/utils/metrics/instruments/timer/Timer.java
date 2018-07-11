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
package org.eclipse.ditto.services.utils.metrics.instruments.timer;

import org.eclipse.ditto.services.utils.metrics.instruments.MetricInstrument;

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
