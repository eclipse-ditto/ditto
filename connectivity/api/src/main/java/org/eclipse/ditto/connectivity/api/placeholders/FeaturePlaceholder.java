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
package org.eclipse.ditto.connectivity.api.placeholders;

import org.eclipse.ditto.placeholders.Placeholder;

/**
 * A {@link org.eclipse.ditto.placeholders.Placeholder} that requires a {@code String}
 * (a valid Feature ID) to resolve its placeholders.
 * A {@link org.eclipse.ditto.placeholders.Placeholder} that requires a {@code String} (a valid Feature ID) to resolve its placeholders.
 *
 * @since 1.5.0
 */
public interface FeaturePlaceholder extends Placeholder<CharSequence> {
}
