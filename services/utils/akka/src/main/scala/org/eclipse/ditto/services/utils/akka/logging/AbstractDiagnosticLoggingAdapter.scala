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
package org.eclipse.ditto.services.utils.akka.logging

import akka.event.DiagnosticLoggingAdapter
import javax.annotation.concurrent.NotThreadSafe

/**
  * This class exists to be able to implement [[DiagnosticLoggingAdapter]] in Java.
  */
@NotThreadSafe
abstract class AbstractDiagnosticLoggingAdapter extends DiagnosticLoggingAdapter {

  /** Removes the currently set correlation ID from the MDC.
    */
  def discardCorrelationId(): Unit

}
