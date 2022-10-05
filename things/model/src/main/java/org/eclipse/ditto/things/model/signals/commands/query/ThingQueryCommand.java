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
package org.eclipse.ditto.things.model.signals.commands.query;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.WithSelectedFields;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;

/**
 * Aggregates all {@link org.eclipse.ditto.things.model.signals.commands.ThingCommand}s which query the state of things (read, ...).
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingQueryCommand<T extends ThingQueryCommand<T>> extends ThingCommand<T>, WithSelectedFields {

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    @Override
    default Category getCategory() {
        return Category.QUERY;
    }
}
