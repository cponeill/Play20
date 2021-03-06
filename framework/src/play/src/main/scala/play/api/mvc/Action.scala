package play.api.mvc

import play.api.libs.iteratee._

/**
 * An Handler handles a request.
 */
trait Handler

class HandlerRef[T](callValue: => T, handlerDef: play.core.Router.HandlerDef)(implicit handlerInvoker: play.core.Router.HandlerInvoker[T]) extends play.mvc.HandlerRef {

  def handler: play.api.mvc.Handler = {
    handlerInvoker.call(callValue, handlerDef)
  }

  lazy val sym = {
    handlerDef.controller + "." + handlerDef.method + "(" + handlerDef.parameterTypes.map(_.getName).mkString(", ") + ")"
  }

  override def toString = {
    "HandlerMock[" + sym + ")]"
  }

}

/**
 * An action is essentially a (Request[A] => Result) function that
 * handles a request and generates a result to be sent to the client.
 *
 * For example,
 * {{{
 * val echo = Action { request =>
 *   Ok("Got request [" + request + "]")
 * }
 * }}}
 *
 * @tparam A the type of the request body
 */
trait Action[A] extends (Request[A] => Result) with Handler {

  /** Type of the request body. */
  type BODY_CONTENT = A

  /**
   * Body parser associated with this action.
   *
   * @see BodyParser
   */
  def parser: BodyParser[A]

  /**
   * Invokes this action.
   *
   * @param request the incoming HTTP request
   * @return the result to be sent to the client
   */
  def apply(request: Request[A]): Result

  /**
   * Returns itself, for better support in the routes file.
   *
   * @return itself
   */
  def apply() = this

}

/**
 * A body parser parses the HTTP request body content.
 *
 * @tparam T the body content type
 */
trait BodyParser[+A] extends Function1[RequestHeader, Iteratee[Array[Byte], Either[Result, A]]] {

  def map[B](f: A => B): BodyParser[B] = BodyParser { request =>
    this(request).map(_.right.map(f(_)))
  }

}

/** Helper object to construct `BodyParser` values. */
object BodyParser {

  def apply[T](f: Function1[RequestHeader, Iteratee[Array[Byte], Either[Result, T]]]) = new BodyParser[T] {
    def apply(rh: RequestHeader) = f(rh)
  }

}

/** Helper object to create `Action` values. */
object Action {

  /**
   * Constructs an `Action`.
   *
   * For example:
   * {{{
   * val echo = Action(parse.anyContent) { request =>
   *   Ok("Got request [" + request + "]")
   * }
   * }}}
   *
   * @tparam A the type of the request body
   * @param bodyParser the `BodyParser` to use to parse the request body
   * @param block the action code
   * @return an action
   */
  def apply[A](bodyParser: BodyParser[A])(block: Request[A] => Result): Action[A] = new Action[A] {
    def parser = bodyParser
    def apply(ctx: Request[A]) = block(ctx)
  }

  /**
   * Constructs an `Action` with default content.
   *
   * For example:
   * {{{
   * val echo = Action { request =>
   *   Ok("Got request [" + request + "]")
   * }
   * }}}
   *
   * @param block the action code
   * @return an action
   */
  def apply(block: Request[AnyContent] => Result): Action[AnyContent] = {
    Action(BodyParsers.parse.anyContent)(block)
  }

  /**
   * Constructs an `Action` with default content, and no request parameter.
   *
   * For example:
   * {{{
   * val hello = Action {
   *   Ok("Hello!")
   * }
   * }}}
   *
   * @param block the action code
   * @return an action
   */
  def apply(block: => Result): Action[AnyContent] = {
    this.apply(_ => block)
  }

}
