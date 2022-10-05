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

import java.util.List;

/**
 * Definition of a placeholder expression in the format {@code prefix:name}.
 *
 * @param <T> the type which is required to resolve a placeholder
 */
public interface Placeholder<T> extends Expression {

    /**
     * Resolves the placeholder variable by name.
     *
     * @param placeholderSource the source from which to the placeholder is resolved, e.g. a Thing id.
     * @param name the placeholder variable name (i. e., the part after ':').
     * @return All values this placeholder resolved for the given placeholder source and the given variable name.
     * @since 2.4.0
     */
    List<String> resolveValues(T placeholderSource, String name);

}
