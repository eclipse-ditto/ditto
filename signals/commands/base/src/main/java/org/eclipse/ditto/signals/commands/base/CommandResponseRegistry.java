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
package org.eclipse.ditto.signals.commands.base;

import org.eclipse.ditto.signals.base.JsonParsableRegistry;

/**
 * Registry aware of a set of {@link CommandResponse}s which it can parse from a {@link
 * org.eclipse.ditto.json.JsonObject}.
 *
 * @param <T> the type of the CommandResponse to parse.
 */
public interface CommandResponseRegistry<T extends CommandResponse> extends JsonParsableRegistry<T> {
}
