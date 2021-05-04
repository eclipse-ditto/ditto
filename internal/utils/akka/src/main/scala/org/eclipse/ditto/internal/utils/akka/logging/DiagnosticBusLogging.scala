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
package org.eclipse.ditto.internal.utils.akka.logging

import akka.event.{BusLogging, DiagnosticLoggingAdapter, LoggingBus, LoggingFilter}

/** This class combines [[BusLogging]] with [[DiagnosticLoggingAdapter]] for usage in Java.
  *
  * @since 1.4.0
  */
final class DiagnosticBusLogging(bus: LoggingBus, logSource: String, logClass: Class[_], loggingFilter: LoggingFilter)
  extends BusLogging(bus, logSource, logClass, loggingFilter) with DiagnosticLoggingAdapter
