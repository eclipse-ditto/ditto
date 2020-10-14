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
package org.eclipse.ditto.model.placeholders;

import org.eclipse.ditto.model.connectivity.ConnectionId;

/**
 * A {@link Placeholder} that requires a {@code String} (a valid Connection ID) to resolve its placeholders.
 *
 * @since 1.4.0
 */
public interface ConnectionIdPlaceholder extends Placeholder<ConnectionId> {
}
