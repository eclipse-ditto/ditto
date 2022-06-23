/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.enforcement;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * Responsible to resolve a field of a referenced entity.
 *
 * @param <T> The type of the field.
 */
interface ReferencePlaceholderResolver<T> {

    CompletionStage<T> resolve(ReferencePlaceholder referencePlaceholder, DittoHeaders dittoHeaders);
}
