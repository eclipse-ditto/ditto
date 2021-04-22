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
package org.eclipse.ditto.base.model.signals.commands;

import org.eclipse.ditto.base.model.signals.JsonParsableRegistry;

/**
 * Registry aware of a set of {@link Command}s which it can parse from a {@link org.eclipse.ditto.json.JsonObject}.
 *
 * @param <T> the type of the Command to parse.
 */
public interface CommandRegistry<T extends Command> extends JsonParsableRegistry<T> {
}
