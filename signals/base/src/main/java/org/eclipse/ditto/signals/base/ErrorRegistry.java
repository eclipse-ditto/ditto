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
package org.eclipse.ditto.signals.base;


import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;

/**
 * An error registry is aware of a set of {@link DittoRuntimeException}s which the registry can parse from a {@link
 * org.eclipse.ditto.json.JsonObject}.
 *
 * @param <T> the type of the DittoRuntimeException to parse.
 */
public interface ErrorRegistry<T extends DittoRuntimeException> extends JsonParsableRegistry<T> {
}
