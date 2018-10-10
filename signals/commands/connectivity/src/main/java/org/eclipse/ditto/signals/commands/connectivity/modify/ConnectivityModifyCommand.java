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
package org.eclipse.ditto.signals.commands.connectivity.modify;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.signals.base.WithOptionalEntity;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;

/**
 * Aggregates all {@link ConnectivityCommand}s which modify the state of a {@link Connection}.
 *
 * @param <T> the type of the implementing class.
 */
public interface ConnectivityModifyCommand<T extends ConnectivityModifyCommand>
        extends ConnectivityCommand<T>, WithOptionalEntity {
}
