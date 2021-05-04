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

import akka.event.DiagnosticLoggingAdapter

import javax.annotation.Nullable
import javax.annotation.concurrent.NotThreadSafe

/** This class exists to be able to implement [[DiagnosticLoggingAdapter]] in Java.
  *
  * Additionally the LoggingAdapter is aware of the correlation ID concept.
  *  I. e. users may provide a correlation ID which then is put to the MDC to enhance log statements.
  */
@NotThreadSafe
abstract class AbstractDiagnosticLoggingAdapter extends DiagnosticLoggingAdapter {

  /** Puts the specified diagnostic context value as identified by the specified key to this logger's MDC until it gets
    * removed.
    * <p>
    * Providing `null` as value has the same effect as calling
    * [[AbstractDiagnosticLoggingAdapter removeMdcEntry(CharSequence)]] with the specified key.
    * </p>
    *
    * @param key   the key which identifies the diagnostic value.
    * @param value the diagnostic value which is identified by `key`.
    * @return this or a new logger instance for method chaining.
    * @throws NullPointerException     if `key` is `null`.
    * @throws IllegalArgumentException if `key` is empty.
    * @see #removeMdcEntry(CharSequence)
    * @since 1.4.0
    */
  def putMdcEntry(key: CharSequence, @Nullable value: CharSequence): AbstractDiagnosticLoggingAdapter

  /** Removes from the MDC the diagnostic context value identified by the specified key.
    * This method does nothing if there is no previous value associated with the specified key.
   *
    * @param key the key of the value to be removed.
    * @return this or a new logger instance for method chaining.
    * @throws NullPointerException     if `key` is `null`.
    * @throws IllegalArgumentException if `key` is empty.
    * @since 1.4.0
   */
  def removeMdcEntry(key: CharSequence): AbstractDiagnosticLoggingAdapter

  /** Removes from the MDC all diagnostic context values this logger is aware of.
    *
    * @return this or a new logger instance for method chaining.
    */
  def discardMdcEntries(): AbstractDiagnosticLoggingAdapter

  /** Returns the name of this logger.
    *
    * @return the logger's name.
    * @since 1.4.0
    */
  def getName: String

}
