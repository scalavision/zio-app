package zio.app.internal

import boopickle.Default._
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues}
import zhttp.http._
import zio._

import java.nio.ByteBuffer

object Utils {
  private val bytesContent: Header = Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.BYTES)

  private def pickle[A: Pickler](value: A): UResponse = {
    val bytes: ByteBuffer = Pickle.intoBytes(value)
    val byteBuf           = Unpooled.wrappedBuffer(bytes)
    val httpData          = HttpData.fromByteBuf(byteBuf)

    Response.http(status = Status.OK, headers = List(bytesContent), content = httpData)
  }

  def makeRoute[R, E, A: Pickler, B: Pickler](
      service: String,
      method: String,
      call: A => ZIO[R, E, B]
  ): HttpApp[R, E] = {
    val service0 = service
    val method0  = method
    Http.collectM { case post @ Method.POST -> Root / `service0` / `method0` =>
      post.data.content match {
        case HttpData.CompleteData(data) =>
          val byteBuffer = ByteBuffer.wrap(data.toArray)
          val unpickled  = Unpickle[A].fromBytes(byteBuffer)
          call(unpickled).map(pickle[B](_))
        case _ => UIO(Response.ok)
      }
    }
  }

  def makeRouteNullary[R, E, A: Pickler](
      service: String,
      method: String,
      call: ZIO[R, E, A]
  ): HttpApp[R, E] = {
    val service0 = service
    val method0  = method
    Http.collectM { case Method.GET -> Root / `service0` / `method0` =>
      call.map(pickle[A](_))
    }
  }

}
