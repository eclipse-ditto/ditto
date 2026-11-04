/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.persistence.api.write;

import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;

/**
 * Backend-neutral output element of the search updater flow: the write model that was applied and the
 * backend-neutral result of applying it.
 *
 * @param writeModel the write model that was applied.
 * @param result the backend-neutral result of applying the write model.
 * @since 4.0.0
 */
public record UpdaterResult(AbstractWriteModel writeModel, SearchWriteResult result) {}
