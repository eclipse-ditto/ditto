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
package org.eclipse.ditto.policies.model.signals.commands.query;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;

/**
 * Aggregates all PolicyCommands which query the state of policies (read, ...).
 *
 * @param <T> the type of the implementing class.
 */
public interface PolicyQueryCommand<T extends PolicyQueryCommand<T>> extends PolicyCommand<T> {

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    @Override
    default Category getCategory() {
        return Category.QUERY;
    }
}
