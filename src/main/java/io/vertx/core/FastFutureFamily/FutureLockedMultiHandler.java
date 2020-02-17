package io.vertx.core.FastFutureFamily;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;

import java.util.ArrayList;

public class FutureLockedMultiHandler<T> implements Promise<T>, LockedFuture<T>
{
  private boolean isPromiseCreated = false;
  private T result;
  private Throwable error;

  private final ArrayList<HandlerWithContext<T>> handlerWithContext = new ArrayList<HandlerWithContext<T>>();

  private final VertxInternal vertx;

  public FutureLockedMultiHandler(VertxInternal vertx)
  {
    this.vertx = vertx;
  }


  @Override
  public boolean isComplete()
  {
    return result != null || error != null;
  }


  @Override
  public Future<T> onComplete(Handler<AsyncResult<T>> handler)
  {
    //thats more an assert-check, than an security check
    //if it would be an security check it should be in the synchronized, and on other positions in the code i need that synchronized too, then
    //that is what i dont want to have
    if (isPromiseCreated)
    {
      throw new RuntimeException("if promise is created, no more handler are allowed to register");
    }


    ContextInternal callerContext = vertx.getOrCreateContext();
    HandlerWithContext<T> handlerWithContext = new HandlerWithContext<>(handler, callerContext);

    /**
     * synchronized: so different Threads can register its onCompleteHandler in parallel
     *
     * at completion time there is only one thread active
     */
    synchronized (this.handlerWithContext)
    {
      this.handlerWithContext.add(handlerWithContext);
      return this;
    }

  }

  @Override
  public Future<T> onComplete(Handler<AsyncResult<T>> handler, long timeout)
  {
    //assertioncheck
    if (isPromiseCreated)
    {
      throw new RuntimeException("if promise is created, no more handler are allowed to register");
    }

    ContextInternal callerContext = vertx.getOrCreateContext();
    TimeoutWrapperHandler<T> timeoutWrapperHandler = new TimeoutWrapperHandler<>(handler, callerContext, 0);
    HandlerWithContext<T> handlerWithContext = new HandlerWithContext<>(timeoutWrapperHandler, callerContext);

    /**
     * synchronized: so different Threads can register its onCompleteHandler in parallel
     *
     * at completion time there is only one thread active
     */
    synchronized (this.handlerWithContext)
    {
      this.handlerWithContext.add(handlerWithContext);
    }

    timeoutWrapperHandler.startTimeout(timeout);
    return this;
  }



  @Override
  public Handler<AsyncResult<T>> getHandler()
  {
    return null;
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

    for (HandlerWithContext<T> value : this.handlerWithContext)
    {
      value.doDispatch(this);
    }
    return true;
  }

  @Override
  public boolean tryFail(Throwable cause)
  {
    if (result != null && error != null) return false;
    this.error = cause;
    for (HandlerWithContext<T> value : this.handlerWithContext)
    {
      value.doDispatch(this);
    }
    return true;
  }


  @Override
  public Future<T> future()
  {
    return this;
  }

  public Promise<T> promise()
  {
    this.isPromiseCreated = true;
    return this;
  }



}
