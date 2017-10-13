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
package org.eclipse.ditto.signals.commands.live.assertions;

import org.eclipse.ditto.signals.commands.base.assertions.AbstractCommandAssert;
import org.eclipse.ditto.signals.commands.live.base.LiveCommand;

/**
 * An Assert for {@link LiveCommand}s.
 */
public class LiveCommandAssert extends AbstractCommandAssert<LiveCommandAssert, LiveCommand> {

    /**
     * Constructs a new {@code LiveCommandAssert} object.
     *
     * @param actual the command response to be checked.
     */
    public LiveCommandAssert(final LiveCommand actual) {
        super(actual, LiveCommandAssert.class);
    }

}
