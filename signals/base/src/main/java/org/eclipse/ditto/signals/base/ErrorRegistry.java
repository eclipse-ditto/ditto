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
