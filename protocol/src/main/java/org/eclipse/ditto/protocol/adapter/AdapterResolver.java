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
package org.eclipse.ditto.protocol.adapter;

import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.base.model.signals.Signal;

/**
 * Resolves the matching {@link Adapter} for the given {@link org.eclipse.ditto.protocol.Adaptable}.
 */
interface AdapterResolver {

    /**
     * Select the correct {@link Adapter} (e.g. things/policy, query/modify/...) for the given
     * {@link org.eclipse.ditto.protocol.Adaptable}.
     *
     * @param adaptable the adaptable that is converted to a {@link Signal}
     * @return the appropriate {@link org.eclipse.ditto.protocol.Adaptable} capable of converting the {@link org.eclipse.ditto.protocol.Adaptable} to a {@link Signal}
     */
    Adapter<? extends Signal<?>> getAdapter(Adaptable adaptable);

}
