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
package org.eclipse.ditto.signals.events.things;

import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * This interface describes an event which is emitted after a {@link org.eclipse.ditto.model.things.Thing} was modified.
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingModifiedEvent<T extends ThingModifiedEvent> extends ThingEvent<T> {

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);
}
