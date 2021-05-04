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
package org.eclipse.ditto.internal.utils.akka

import akka.actor.{ActorSystem, DynamicAccess, ExtendedActorSystem}

import java.util.Collections
import scala.collection.JavaConverters.asScalaBuffer
import scala.collection.immutable
import scala.reflect.ClassTag

/** Java-friendly interface to load and instantiate classes inside an actor system. */
object AkkaClassLoader {

  /** Dynamically instantiate a class with its zero-argument public constructor.
    *
    * @param actorSystem   Actor system to create the object in.
    * @param superclass    A superclass of the instantiated class.
    * @param canonicalName Canonical name of the class. Must have a zero-argument public constructor.
    * @tparam T the superclass.
    * @return The instantiated object.
    */
  def instantiate[T](actorSystem: ActorSystem, superclass: Class[T], canonicalName: String): T =
    instantiate(actorSystem, superclass, canonicalName, Collections.emptyList(), Collections.emptyList())

  /** Dynamically instantiate a class with a public constructor.
    *
    * @param actorSystem   Actor system to create the object in.
    * @param superclass    A superclass of the instantiated class.
    * @param canonicalName Canonical name of the class.
    * @param argTypes      Classes of the constructor arguments.
    * @param args          Constructor arguments.
    * @tparam T the superclass.
    * @return The instantiated object.
    */
  def instantiate[T](actorSystem: ActorSystem, superclass: Class[T],
                     canonicalName: String,
                     argTypes: java.util.List[Class[_]],
                     args: java.util.List[AnyRef]): T = {

    val dynamicAccess: DynamicAccess = actorSystem.asInstanceOf[ExtendedActorSystem].dynamicAccess
    val argsAsScala: immutable.Seq[(Class[_], AnyRef)] = asScalaBuffer(argTypes).toList.zip(asScalaBuffer(args))
    val classTag: ClassTag[T] = ClassTag(superclass)

    dynamicAccess.createInstanceFor(canonicalName, argsAsScala)(classTag).get
  }

}
