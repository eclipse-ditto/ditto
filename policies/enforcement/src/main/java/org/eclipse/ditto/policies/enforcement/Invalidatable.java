/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.policies.enforcement;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.policies.api.PolicyTag;

public interface Invalidatable {

    CompletionStage<Boolean> invalidate(PolicyTag policyTag, String correlationId, final Duration askTimeout);
}
