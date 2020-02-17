package io.vertx.core.FastFutureFamily;

import io.vertx.core.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;

public class FutureLockedOneHandler<T> implements Promise<T>, Future<T>
{
  private T result;
  private Throwable error;
  private final Handler<AsyncResult<T>> handler;
  private final ContextInternal handlerContext;

  public FutureLockedOneHandler(VertxInternal vertx, Handler<AsyncResult<T>> handler)
  {
    this.handler = handler;
    this.handlerContext = vertx.getOrCreateContext();
  }

  public FutureLockedOneHandler(VertxInternal vertx, Handler<AsyncResult<T>> handler, long timeout)
  {
    this.handlerContext = vertx.getOrCreateContext();
    this.handler = new TimeoutWrapperHandler<T>(handler, this.handlerContext, timeout);
  }

  public FutureLockedOneHandler(Handler<AsyncResult<T>> handler, ContextInternal forcedHandlerContext)
  {
    this.handler = handler;
    this.handlerContext = forcedHandlerContext;
  }

  public FutureLockedOneHandler(Handler<AsyncResult<T>> handler, ContextInternal forcedHandlerContext, long timeout)
  {
    this.handlerContext = forcedHandlerContext;
    this.handler = new TimeoutWrapperHandler<T>(handler, this.handlerContext, timeout);
  }


  @Override
  public boolean isComplete()
  {
    return result != null || error != null;
  }

  @Override
  public Future<T> onComplete(Handler<AsyncResult<T>> handler)
  {
    throw new RuntimeException("this method is in that implementation not operatable");
  }

  @Override
  public Future<T> onComplete(Handler<AsyncResult<T>> handler, long timeout)
  {
    throw new RuntimeException("this method is in that implementation not operatable");
  }

  @Override
  public Handler<AsyncResult<T>> getHandler()
  {
    return this.handler;
  }

  @Override
  public T result()
  {
    return this.result;
  }

  @Override
  public Throwable cause()
  {
    return this.error;
  }

  @Override
  public boolean succeeded()
  {
    return this.result != null;
  }

  @Override
  public boolean failed()
  {
    return this.error != null;
  }

  @Override
  public boolean tryComplete(T result)
  {
    if (result != null && error != null) return false;
    this.result = result;
    this.doDispatch(handler);
    return true;
  }

  @Override
  public boolean tryFail(Throwable cause)
  {
    if (result != null && error != null) return false;
    this.error = cause;
    this.doDispatch(handler);
    return true;
  }


  private void doDispatch(Handler<AsyncResult<T>> handler)
  {
    this.doDispatch(this, handler);
  }

  private void doDispatch(AsyncResult<T> argument, Handler<AsyncResult<T>> handler)
  {
    if (this.handlerContext != null)
    {
      this.handlerContext.dispatch(this, handler);
    }
    else
    {
      handler.handle(this);
    }
  }

  @Override
  public Future<T> future()
  {
    return this;
  }

//  @Override
//  public Promise<T> promise()
//  {
//    if(this.handler ==null) throw new IllegalStateException("an handler must be registerd");
//    return this;
//  }
}
