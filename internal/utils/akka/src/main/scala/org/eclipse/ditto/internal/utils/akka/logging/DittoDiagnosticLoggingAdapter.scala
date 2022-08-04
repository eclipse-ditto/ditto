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
package org.eclipse.ditto.internal.utils.akka.logging

import org.eclipse.ditto.base.model.headers.{DittoHeaders, WithDittoHeaders}

import javax.annotation.Nullable
import javax.annotation.concurrent.NotThreadSafe
import scala.annotation.varargs

/** An Akka [[akka.event.DiagnosticLoggingAdapter]] with additional functionality.
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

  /** Sets the given correlation ID for all subsequent log operations until it gets manually discarded.
   *
   * @param correlationId the correlation ID to be put to the MDC.
   * @return this logger instance to allow method chaining.
   */
  def setCorrelationId(@Nullable correlationId: CharSequence): DittoDiagnosticLoggingAdapter

  /** Derives the correlation ID from the given WithDittoHeaders for all subsequent log operations until it gets
   * manually discarded.
   *
   * @param withDittoHeaders provides DittoHeaders which might contain the correlation ID to be put to the MDC.
   * @return this logger instance to allow method chaining.
   * @throws NullPointerException if `withDittoHeaders` is `null`.
   */
  def setCorrelationId(withDittoHeaders: WithDittoHeaders): DittoDiagnosticLoggingAdapter

  /** Obtains the correlation ID from the given DittoHeaders for all subsequent log operations until it gets manually
   * discarded.
   *
   * @param dittoHeaders might contain the correlation ID to be put to the MDC.
   * @return this logger instance to allow method chaining.
   * @throws NullPointerException if `dittoHeaders` is `null`.
   */
  def setCorrelationId(dittoHeaders: DittoHeaders): DittoDiagnosticLoggingAdapter

  /** Removes the correlation ID from the MDC for all subsequent log operations. */
  def discardCorrelationId(): Unit

  /** Sets the specified diagnostic context value as identified by the specified key to this logger's MDC for all
   * subsequent log operations until it gets manually discarded.
   * <p>
   * Providing `null` as value has the same effect as calling [[MdcEntrySettable removeMdcEntry(CharSequence)]] with
   * the specified key.
   * </p>
   *
   * @param key   the key which identifies the diagnostic value.
   * @param value the diagnostic value which is identified by `key`.
   * @return this logger instance to allow method chaining.
   * @throws NullPointerException     if `key` is `null`.
   * @throws IllegalArgumentException if `key` is empty.
   */
  def setMdcEntry(key: CharSequence, @Nullable value: CharSequence): DittoDiagnosticLoggingAdapter

  /** Sets the specified diagnostic context values as identified by the specified keys to this logger's MDC for all
   * subsequent log operations until it gets manually discarded.
   * <p>
   * Providing `null` for any value has the same effect as calling [[MdcEntrySettable removeMdcEntry(CharSequence)]]
   * with its associated key.
   * </p>
   *
   * @param k1 the first key which identifies the diagnostic value `v1`.
   * @param v1 the first diagnostic value which is identified by `k1`.
   * @param k2 the second key which identifies the diagnostic value `v2`.
   * @param v2 the second diagnostic value which is identified by `k2`.
   * @return this logger instance to allow method chaining.
   * @throws NullPointerException     if `k1` or `k2` is `null`.
   * @throws IllegalArgumentException if `k1` or `k2` is empty.
   */
  def setMdcEntries(k1: CharSequence, @Nullable v1: CharSequence,
                    k2: CharSequence, @Nullable v2: CharSequence): DittoDiagnosticLoggingAdapter

  /** Sets the specified diagnostic context values as identified by the specified keys to this logger's MDC for all
   * subsequent log operations until it gets manually discarded.
   * <p>
   * Providing `null` for any value has the same effect as calling [[MdcEntrySettable removeMdcEntry(CharSequence)}]]
   * with its associated key.
   * </p>
   *
   * @param k1 the first key which identifies the diagnostic value `v1`.
   * @param v1 the first diagnostic value which is identified by `k1`.
   * @param k2 the second key which identifies the diagnostic value `v2`.
   * @param v2 the second diagnostic value which is identified by `k2`.
   * @param k3 the third key which identifies the diagnostic value `v3`.
   * @param v3 the third diagnostic value which is identified by `k3`.
   * @return this logger instance to allow method chaining.
   * @throws NullPointerException     if `k1`, `k2` or `k3` is `null`.
   * @throws IllegalArgumentException if `k1`, `k2` or `k3` is empty.
   */
  def setMdcEntries(k1: CharSequence, @Nullable v1: CharSequence,
                    k2: CharSequence, @Nullable v2: CharSequence,
                    k3: CharSequence, @Nullable v3: CharSequence): DittoDiagnosticLoggingAdapter

  /** Removes the currently set diagnostic value from the MDC which is identified by the specified key.
   *
   * @param key the key which identifies the value to be discarded.
   */
  def discardMdcEntry(key: CharSequence): Unit

  /** Sets the specified diagnostic context values as identified by the specified keys to this logger's MDC for all
   * subsequent log operations until it gets manually discarded.
   *
   * @return this logger instance to allow method chaining.
   * @throws NullPointerException if any argument is `null`.
   */
  @annotation.varargs def setMdcEntry(mdcEntry: MdcEntry, furtherMdcEntries: MdcEntry*): DittoDiagnosticLoggingAdapter

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
