/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
 * (a {@link org.eclipse.ditto.connectivity.model.Source} {@code address}) to resolve its placeholders.
 */
public interface SourceAddressPlaceholder extends Placeholder<String> {

}
