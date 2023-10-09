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
package org.eclipse.ditto.internal.utils.pekko.logging

import org.eclipse.ditto.base.model.headers.{DittoHeaders, WithDittoHeaders}

import java.util
import javax.annotation.Nullable
import javax.annotation.concurrent.NotThreadSafe
import scala.annotation.varargs

/** An Pekko [[org.apache.pekko.event.DiagnosticLoggingAdapter]] with additional functionality.
 *
 * The main purpose of DittoDiagnosticLoggingAdapter is to provide an API for easily setting a correlation ID to the
 * logging MDC.
 *
 * DittoDiagnosticLoggingAdapter can be used in two ways:
 * <ol>
 * <li>set the correlation ID per log operation, i. e. it gets automatically discarded immediately after having
 * performed the log operation.</li>
 * <li>Or set the correlation ID globally until it gets manually discarded.</li>
 * </ol>
 *
 * The following example shows usage for case 1.
 * The correlation ID is only logged for the info message and gets discarded automatically afterwards.
 * <pre>
 * DittoDiagnosticLoggingAdapter dittoLogger = DittoLoggerFactory.getDiagnosticLoggingAdapter(myActor);
 *  dittoLogger.withCorrelationId("my-correlation-id").info("This is a normal log operation.");
 *  dittoLogger.warn("The correlation ID is logged no more.");
 * </pre>
 *
 * For case 2 the correlation ID is set globally and will be logged until it gets discarded manually.
 * <pre>
 * DittoDiagnosticLoggingAdapter dittoLogger = DittoLoggerFactory.getDiagnosticLoggingAdapter(myActor);
 *
 *  dittoLogger.setCorrelationId("my-correlationId"); // Ignore the returned value
 *  dittoLogger.info("This message logs the correlation ID.");
 *  dittoLogger.info("So does this message.");
 *  dittoLogger.info("And this.");
 *  dittoLogger.discardCorrelationId();
 *
 *  dittoLogger.warn("The correlation ID is logged no more.");
 * </pre>
 */
@NotThreadSafe
abstract class DittoDiagnosticLoggingAdapter extends AbstractDiagnosticLoggingAdapter
  with MdcEntrySettable[DittoDiagnosticLoggingAdapter] {

  /** Sets the given correlation ID for the subsequent log operation.
   *
   * @param correlationId the correlation ID to be put to the MDC.
   * @return this DittoLogger instance to allow method chaining.
   */
  def withCorrelationId(@Nullable correlationId: CharSequence): DittoDiagnosticLoggingAdapter

  /** Obtains the correlation ID from the given headers for the subsequent log operation.
    *
    * @param headers might contain the correlation ID to be put to the MDC.
    * @return this DittoLogger instance to allow method chaining.
    */
  def withCorrelationId(@Nullable headers: util.Map[String, String]): DittoDiagnosticLoggingAdapter

  /** Derives the correlation ID from the given WithDittoHeaders for the subsequent log operation.
   *
   * @param withDittoHeaders provides DittoHeaders which might contain the correlation ID to be put to the MDC.
   * @return this DittoLogger instance to allow method chaining.
   */
  def withCorrelationId(@Nullable withDittoHeaders: WithDittoHeaders): DittoDiagnosticLoggingAdapter

  /** Obtains the correlation ID from the given DittoHeaders for the subsequent log operation.
   *
   * @param dittoHeaders might contain the correlation ID to be put to the MDC.
   * @return this DittoLogger instance to allow method chaining.
   */
  def withCorrelationId(@Nullable dittoHeaders: DittoHeaders): DittoDiagnosticLoggingAdapter

  /** Removes the correlation ID from the MDC for all subsequent log operations. */
  def discardCorrelationId(): Unit

  /** Removes the currently set diagnostic value from the MDC which is identified by the specified key.
   *
   * @param key the key which identifies the value to be discarded.
   */
  def discardMdcEntry(key: CharSequence): Unit

  /** Message template with > 4 replacement arguments. */
  @varargs def error(throwable: Throwable, template: String, arg1: Any, arg2: Any, arg3: Any, arg4: Any, moreArgs: Any*): Unit = {
    if (isErrorEnabled) {
      val array = Array(arg1, arg2, arg3, arg4)
      val combined = Array.concat(array, moreArgs.toArray)
      error(throwable, template, combined)
    }
  }

  /** Message template with > 4 replacement arguments. */
  @varargs def error(template: String, arg1: Any, arg2: Any, arg3: Any, arg4: Any, moreArgs: Any*): Unit = {
    if (isErrorEnabled) {
      val array = Array(arg1, arg2, arg3, arg4)
      val combined = Array.concat(array, moreArgs.toArray)
      error(template, combined)
    }
  }

  /** Message template with > 4 replacement arguments. */
  @varargs def warning(template: String, arg1: Any, arg2: Any, arg3: Any, arg4: Any, moreArgs: Any*): Unit = {
    if (isWarningEnabled) {
      val array = Array(arg1, arg2, arg3, arg4)
      val combined = Array.concat(array, moreArgs.toArray)
      warning(template, combined)
    }
  }

  /** Message template with > 4 replacement arguments. */
  @varargs def info(template: String, arg1: Any, arg2: Any, arg3: Any, arg4: Any, moreArgs: Any*): Unit = {
    if (isInfoEnabled) {
      val array = Array(arg1, arg2, arg3, arg4)
      val combined = Array.concat(array, moreArgs.toArray)
      info(template, combined)
    }
  }

  /** Message template with > 4 replacement arguments. */
  @varargs def debug(template: String, arg1: Any, arg2: Any, arg3: Any, arg4: Any, moreArgs: Any*): Unit = {
    if (isDebugEnabled) {
      val array = Array(arg1, arg2, arg3, arg4)
      val combined = Array.concat(array, moreArgs.toArray)
      debug(template, combined)
    }
  }

}
