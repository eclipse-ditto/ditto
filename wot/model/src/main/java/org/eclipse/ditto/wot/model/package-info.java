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

/**
 * This package contains the Eclipse Ditto W3C WoT (Web of Things) model - its main entities being
 * {@link org.eclipse.ditto.wot.model.ThingDescription} (short: TD) and {@link org.eclipse.ditto.wot.model.ThingModel}
 * (short: TM).
 * It also contains builder to build and enhance all aspects of TDs and TMs.
 * <p>
 * As of version {@code 2.4.0} of Ditto, this implementation follows
 * <a href="https://www.w3.org/TR/wot-thing-description11/">Web of Things (WoT) Thing Description 1.1</a>.
 *
 * @since 2.4.0
 */
@org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault
package org.eclipse.ditto.wot.model;