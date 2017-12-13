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
package org.eclipse.ditto.protocoladapter;

import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * Contract for the Ditto Protocol Adapter library. Provides methods for mapping {@link Command} and {@link Event}
 * instances to an {@link Adaptable}.
 */
public final class DittoProtocolAdapter extends AbstractProtocolAdapter {


    private DittoProtocolAdapter() {
        super();
    }

    public static DittoProtocolAdapter newInstance() {
        return new DittoProtocolAdapter();
    }

}
