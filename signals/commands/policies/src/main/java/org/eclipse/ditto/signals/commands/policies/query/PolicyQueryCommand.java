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
package org.eclipse.ditto.signals.commands.policies.query;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;

/**
 * Aggregates all PolicyCommands which query the state of policies (read, ...).
 *
 * @param <T> the type of the implementing class.
 */
public interface PolicyQueryCommand<T extends PolicyQueryCommand> extends PolicyCommand<T> {

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    @Override
    default Category getCategory() {
        return Category.QUERY;
    }
}
