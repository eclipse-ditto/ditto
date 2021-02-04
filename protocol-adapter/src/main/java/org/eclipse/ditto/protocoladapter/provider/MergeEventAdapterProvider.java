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
package org.eclipse.ditto.protocoladapter.provider;

import org.eclipse.ditto.protocoladapter.Adapter;
import org.eclipse.ditto.signals.events.things.ThingMerged;

/**
 * Interface providing the merged event adapter.
 *
 * @since 2.0.0
 */
interface MergeEventAdapterProvider {

    /**
     * @return the merged event adapter
     */
    Adapter<ThingMerged> getMergedEventAdapter();

}
