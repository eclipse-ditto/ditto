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
package org.eclipse.ditto.services.utils.distributedcache.actors;

import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;

/**
 * Command for modifying a {@link CacheEntry} via a
 * {@link CacheFacadeActor}.
 */
public interface CacheModifyCommand extends CacheCommand {

    /**
     * Returns the write consistency of this command.
     *
     * @return the write consistency.
     */
    WriteConsistency getWriteConsistency();

}
