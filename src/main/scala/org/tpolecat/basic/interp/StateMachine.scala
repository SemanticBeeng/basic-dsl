package org.tpolecat.basic.interp

import scalaz.\/
import scalaz.\/-
import scalaz.State
import scalaz.StateT
import scala.collection.immutable.SortedMap
import org.tpolecat.basic.ast.Base
import scalaz.effect.IO
import scalaz.EitherT
import scalaz.effect.MonadIO

trait StateMachine extends Base {

  type Error
  type Variant

  // The type of operations in our interpreter.
  type Op[+A] = StateT[({type λ[+α] = EitherT[IO, Halted, α]})#λ, Running, A]

  // An alias for the type lambda above; this makes lifting easier.
  // Given a State[Running, A] we can say sra.lift[Answer] to get a Op[A]
  type Answer[+A] = EitherT[IO, Halted, A]

  // MonadIO instance for Op
  // Given an IO[A] we can say ioa.liftIO[Op] to get an Op[A]
  implicit object OpMonadIO extends MonadIO[Op] {
    def point[A](a: => A): Op[A] = unit(a)
    def bind[A, B](fa: Op[A])(f: A => Op[B]): Op[B] = fa.flatMap(f)
    def liftIO[A](ioa: IO[A]): Op[A] = StateT[Answer, Running, A] { r =>
      EitherT(ioa.map(a => \/-((r, a))))
    }
  }

  /**
   * State of a running machine, with the program, program counter, stack, and bindings.
   */
  sealed case class Running(
    p: Program,
    pc: Int,
    stack: List[Option[Int]] = Nil,
    bindings: Map[Symbol, Variant] = Map(),
    loops: List[(Symbol, Int, Int)] = Nil) {

    require(p.isDefinedAt(pc)) // TODO: program should be a zipper, making this unnecessary

    /** Compute the next line number after `n`, if any. */
    def next(n: Int): Option[Int] = p.keys.filter(_ > n).headOption

  }

  /** State of a halted machine, with the last known running state and the `Error` that halted execution, if any. */
  sealed case class Halted(finalState: Running, error: Option[Error])

  // LIFTED STATE OPERATIONS

  /** Operation to return the current `Running` state. */
  def get: Op[Running] = State.get.lift[Answer]

  /** Operation to return the current `Running` state, mapped to some type `A`. */
  def gets[A](f: Running => A): Op[A] = State.gets(f).lift[Answer]

  /** Operation to modify the current `Running` state. */
  def modify(f: Running => Running): Op[Unit] = State.modify(f).lift[Answer]

  /** Operation to replace the current `Running` state. */
  def put(s: Running): Op[Unit] = State.put(s).lift[Answer]

  /** Operation that returns the passed value. */
  def unit[A](a: A): Op[A] = State.state(a).lift[Answer]

}
