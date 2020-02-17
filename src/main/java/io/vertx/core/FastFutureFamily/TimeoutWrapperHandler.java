package io.vertx.core.FastFutureFamily;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;

public class TimeoutWrapperHandler<T> implements Handler<AsyncResult<T>>
{
  boolean isFired = false;
  long timerId = -1;
  private final Handler<AsyncResult<T>> handler;
  private final ContextInternal handlerContext;

  public TimeoutWrapperHandler(Handler<AsyncResult<T>> handler, ContextInternal handlerContext, long timeout)
  {
    this.handler = handler;
    this.handlerContext = handlerContext;

    //question can that be critical, if i give an instance of 99% constructed-object to the outside?
    //if yes, then we have there an problem; an extra setterMethod for this is very critically
    //maybe an init-method, which must be called after constructing, else the timeout wont works
    //i know that exposing "pointer/reference" to an unconstructed object can be critically in certain inheritance-scenarious

    if(timeout<=0)return;

    handlerContext.setTimer(timeout, h ->
    {
      this.timeoutHandle(Future.failedFuture(new FutureTimeoutException()));
    });
  }

  public void startTimeout(long timeout)
  {
    //assert-check
    if (timerId != -1) throw new IllegalStateException("timeout is already started");
    if(timeout<=0)return;

    handlerContext.setTimer(timeout, h ->
    {
      this.timeoutHandle(Future.failedFuture(new FutureTimeoutException()));
    });
  }


  //same eventloop -> no synchroniziation needed
  @Override
  public void handle(AsyncResult<T> event)
  {
    if (!isFired)
    {
      isFired = true;
      //handlerContext.cancelTimer(this.timerId); //method is missing in Context! //Todo
      handlerContext.owner().cancelTimer(this.timerId);
      handler.handle(event);
    }
  }

  //same eventloop -> no synchroniziation needed
  public void timeoutHandle(AsyncResult<T> event)
  {
    if (!isFired)
    {
      isFired = true;
      handler.handle(event);
    }
  }


}
