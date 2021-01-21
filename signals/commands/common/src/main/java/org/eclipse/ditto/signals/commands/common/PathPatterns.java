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
package org.eclipse.ditto.signals.commands.common;

import java.util.regex.Pattern;

/**
 * Defines existing path patterns for entities.
 *
 * TODO adapt since annotation @since 1.6.0
 */
public interface PathPatterns {

    /**
     * Returns the path of a PathPatterns enum entry.
     *
     * @return the internal command name.
     */
    String getPath();

    /**
     * Returns the path pattern regex of a PathPatterns enum entry.
     *
     * @return the path pattern regex.
     */
    Pattern getPathPattern();
}
