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
package org.eclipse.ditto.base.model.signals;

import org.eclipse.ditto.base.model.entity.id.WithEntityId;

/**
 * An intersection type for all Signals with an entity associated to it.
 */
public interface SignalWithEntityId<T extends SignalWithEntityId<T>> extends Signal<T>, WithEntityId {
}
