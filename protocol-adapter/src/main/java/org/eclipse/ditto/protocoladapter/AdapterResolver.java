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
package org.eclipse.ditto.protocoladapter;

import org.eclipse.ditto.signals.base.Signal;

/**
 * Resolves the matching {@link Adapter} for the given {@link Adaptable}.
 */
interface AdapterResolver {

    /**
     * Select the correct {@link Adapter} (e.g. things/policy, query/modify/...) for the given
     * {@link Adaptable}.
     *
     * @param adaptable the adaptable that is converted to a {@link Signal}
     * @return the appropriate {@link Adaptable} capable of converting the {@link Adaptable} to a {@link Signal}
     */
    Adapter<? extends Signal<?>> getAdapter(final Adaptable adaptable);

}
