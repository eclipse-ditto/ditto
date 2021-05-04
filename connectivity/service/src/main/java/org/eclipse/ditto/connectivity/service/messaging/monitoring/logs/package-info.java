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
/**
 * This package contains classes that are relevant for connection logging. Connection logging isn't to be
 * confused with normal application logging, but is a separate feature for Dittos connections. It provides
 * the ability to create log entries that can be understood by an end user (and are also meant for the eyes of
 * an end user). This will provide end users a little help to find out if their connections behave correctly
 * without having to dig through application logs.
 */
@org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault
package org.eclipse.ditto.connectivity.service.messaging.monitoring.logs;
