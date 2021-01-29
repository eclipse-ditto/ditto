/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter.provider;

import org.eclipse.ditto.protocoladapter.Adapter;
import org.eclipse.ditto.signals.commands.things.modify.MergeThing;
import org.eclipse.ditto.signals.commands.things.modify.MergeThingResponse;

/**
 * Interface providing the mergeThing command adapter and the mergeThing command response adapter.
 *
 * TODO adapt @since annotation @since 1.6.0
 */
interface MergeCommandAdapterProvider {

    /**
     * @return the modify command adapter
     */
    Adapter<MergeThing> getMergeCommandAdapter();

    /**
     * @return the modify command response adapter
     */
    Adapter<MergeThingResponse> getMergeCommandResponseAdapter();

}
