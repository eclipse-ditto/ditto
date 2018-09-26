/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
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
