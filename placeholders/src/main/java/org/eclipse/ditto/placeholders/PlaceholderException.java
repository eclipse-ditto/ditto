/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.placeholders;

/**
 * Aggregates all {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException}s which are thrown in scope
 * of placeholders.
 */
public interface PlaceholderException {

    /**
     * Error code prefix of errors thrown by the placeholder functionality.
     */
    String ERROR_CODE_PREFIX = "placeholder" + ":";

}
