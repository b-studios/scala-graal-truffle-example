/*
 * Original License from TruffleLMS.scala
 * --------------------------------------
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package example

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.{ Option => _, _ }
import com.oracle.truffle.api.frame._
import com.oracle.truffle.api.nodes._
import com.oracle.truffle.api.TruffleLanguage.{ Env, Registration, ParsingRequest }
import com.oracle.truffle.api.nodes.Node.{ Child, Children }
import com.oracle.truffle.api.source.Source
import org.graalvm.polyglot.{ Context => Ctx }

import scala.annotation.meta.field

trait ExampleContext

// The language is usually registered using some annotation processor.
// Instead, we manually created the file `META-INF/truffle/language`.
class ExampleLang extends TruffleLanguage[ExampleContext] {
  def createContext(env: Env): ExampleContext = {
    ExampleLang.INSTANCE = this;
    new ExampleContext {}
  }
  def isObjectOfLanguage(obj: Any): Boolean = false

  // Here we can implement a parser, later
  override def parse(req: ParsingRequest): CallTarget = ???
}

object ExampleLang {
  private[example] var INSTANCE: ExampleLang = null
  def instance = INSTANCE

  // Truffle/Graal does not allow us to create our own instances
  // of `ExampleLang`. Instead, we need to acquire them via a call
  // to `createContext` or `parse`. This is documented in
  // `RootNode`.
  def apply[T](f: ExampleLang => T): T = {
    val ctx = Ctx.create()
    // this forces a call to createContext
    ctx.initialize("example")
    ctx.enter()
    val res = try {
      f(ExampleLang.INSTANCE)
    } finally {
      ctx.leave()
      ctx.close()
    }
    res
  }

  // We define our own alias, using Scala's meta-annotation
  // (https://www.scala-lang.org/api/current/scala/annotation/meta/index.html)
  // This is necessary to make sure the *field* is annotated, not the getters
  // or setters.
  type child = Child @field
}
import ExampleLang.child


// this is an abstract class to enforce the inheritance to Node
abstract class Exp[@specialized +T] extends Node {
  def apply(frame: VirtualFrame): T
}

// a node that evaluates to constant 42
object FortyTwo extends Exp[Int] {
  def apply(frame: VirtualFrame): Int = 42
}

// a node that adds the two children
final class Add(@child var lhs: Exp[Int], @child var rhs: Exp[Int]) extends Exp[Int] {
  final def apply(frame: VirtualFrame): Int =
    lhs.apply(frame) + rhs.apply(frame)
}
object Add {
  def apply(lhs: Exp[Int], rhs: Exp[Int]) = new Add(lhs, rhs)
}


// Construction an example AST
// ---------------------------
//
// function TestRootNode() {
//    for (i = 100000; i > 0; i--) { SomeFun() }
// }
class TestRootNode(language: ExampleLang) extends RootNode(language) {

  @child private var fun = new SomeFun(language)
  @child private var ct: DirectCallNode = null

  override def execute(frame: VirtualFrame): Integer = {

    if (ct == null) {
      val rt = Truffle.getRuntime
      CompilerDirectives.transferToInterpreterAndInvalidate()
      val callTarget = rt.createCallTarget(fun)
      ct = rt.createDirectCallNode(callTarget)
      adoptChildren()
    }

    var i = 100000
    var res = 0
    while (i > 0) {
      res += ct.call(Array()).asInstanceOf[Int]
      i -= 1
    }

    res
  }
}

// function SomeFun() {
//   result = 0
//   for (count = 10000; count > 0; count--) {
//     result += ((42 + 42) + (42 + 42)) + ((42 + 42) + (42 + 42))
//   }
// }
final class SomeFun(language : ExampleLang) extends RootNode(language)  {

  @child private var repeating = new DummyLoop
  @child private var loop: LoopNode = Truffle.getRuntime.createLoopNode(repeating)

  override def execute(frame: VirtualFrame): Integer = {
    loop.executeLoop(frame)
    repeating.result
  }
}

// Here we use Truffle's special support for a looping construct
class DummyLoop extends Node with RepeatingNode {
  // This will be constant folded
  @child private var child = Add(
      Add(Add(FortyTwo, FortyTwo), Add(FortyTwo, FortyTwo)),
      Add(Add(FortyTwo, FortyTwo), Add(FortyTwo, FortyTwo)))

  private var count = 10000
  var result = 0
  override def executeRepeating(frame: VirtualFrame): Boolean =
    if (count <= 0)
      false
    else {
      val res = child(frame)
      result += res
      count -= 1
      true
    }
}

object Test extends App {
  ExampleLang { lang =>
    val runtime: TruffleRuntime = Truffle.getRuntime
    val rootNode = new TestRootNode(lang)
    val target = runtime.createCallTarget(rootNode)
    println(target.call())
  }
}
