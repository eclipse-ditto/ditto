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

import javax.annotation.Nullable
import javax.annotation.concurrent.NotThreadSafe
import org.eclipse.ditto.model.base.headers.{DittoHeaders, WithDittoHeaders}

/** An Akka [[akka.event.DiagnosticLoggingAdapter]] with additional functionality.
  *
  * The main purpose of DittoDiagnosticLoggingAdapter is to provide an API for easily setting a correlation ID to the
  * logging MDC.
  *
  * DittoDiagnosticLoggingAdapter can be used in two ways:
  * <ol>
  *     <li>set the correlation ID per log operation, i. e. it gets automatically discarded immediately after having
  *     performed the log operation.</li>
  *     <li>Or set the correlation ID globally until it gets manually discarded.</li>
  * </ol>
  *
  * The following example shows usage for case 1.
  * The correlation ID is only logged for the info message and gets discarded automatically afterwards.
  * <pre>
  * DittoDiagnosticLoggingAdapter dittoLogger = DittoLoggerFactory.getDiagnosticLoggingAdapter(myActor);
  * dittoLogger.withCorrelationId("my-correlation-id").info("This is a normal log operation.");
  * dittoLogger.warn("The correlation ID is logged no more.");
  * </pre>
  *
  * For case 2 the correlation ID is set globally and will be logged until it gets discarded manually.
  * <pre>
  * DittoDiagnosticLoggingAdapter dittoLogger = DittoLoggerFactory.getDiagnosticLoggingAdapter(myActor);
  *
  * dittoLogger.setCorrelationId("my-correlationId"); // Ignore the returned value
  * dittoLogger.info("This message logs the correlation ID.");
  * dittoLogger.info("So does this message.");
  * dittoLogger.info("And this.");
  * dittoLogger.discardCorrelationId();
  *
  * dittoLogger.warn("The correlation ID is logged no more.");
  * </pre>
  */
@NotThreadSafe
abstract class DittoDiagnosticLoggingAdapter extends AbstractDiagnosticLoggingAdapter {

  /** Sets the given correlation ID for the subsequent log operation.
    *
    * @param correlationId the correlation ID to be put to the MDC.
    * @return this DittoLogger instance to allow method chaining.
    */
  def withCorrelationId(@Nullable correlationId: CharSequence): DittoDiagnosticLoggingAdapter

  /** Derives the correlation ID from the given WithDittoHeaders for the subsequent log operation.
    *
    * @param withDittoHeaders provides DittoHeaders which might contain the correlation ID to be put to the MDC.
    * @return this DittoLogger instance to allow method chaining.
    * @throws NullPointerException if `withDittoHeaders` is `null`.
    */
  def withCorrelationId(withDittoHeaders: WithDittoHeaders[_]): DittoDiagnosticLoggingAdapter

  /** Obtains the correlation ID from the given DittoHeaders for the subsequent log operation.
    *
    * @param dittoHeaders might contain the correlation ID to be put to the MDC.
    * @return this DittoLogger instance to allow method chaining.
    * @throws NullPointerException if `dittoHeaders` is `null`.
    */
  def withCorrelationId(dittoHeaders: DittoHeaders): DittoDiagnosticLoggingAdapter

  /** Sets the given correlation ID for all subsequent log operations until it gets manually discarded.
    *
    * @param correlationId the correlation ID to be put to the MDC.
    * @return this DittoLogger instance to allow method chaining.
    */
  def setCorrelationId(@Nullable correlationId: CharSequence): DittoDiagnosticLoggingAdapter

  /** Derives the correlation ID from the given WithDittoHeaders for all subsequent log operations until it gets
    * manually discarded.
    *
    * @param withDittoHeaders provides DittoHeaders which might contain the correlation ID to be put to the MDC.
    * @return this DittoLogger instance to allow method chaining.
    * @throws NullPointerException if `withDittoHeaders` is `null`.
    */
  def setCorrelationId(withDittoHeaders: WithDittoHeaders[_]): DittoDiagnosticLoggingAdapter

  /**
    * Obtains the correlation ID from the given DittoHeaders for all subsequent log operations until it gets manually
    * discarded.
    *
    * @param dittoHeaders might contain the correlation ID to be put to the MDC.
    * @return this DittoLogger instance to allow method chaining.
    * @throws NullPointerException if `dittoHeaders` is `null`.
    */
  def setCorrelationId(dittoHeaders: DittoHeaders): DittoDiagnosticLoggingAdapter

}
