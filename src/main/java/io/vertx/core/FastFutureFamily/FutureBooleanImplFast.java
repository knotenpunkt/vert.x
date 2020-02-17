package io.vertx.core.FastFutureFamily;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.FutureBoolean;
import io.vertx.core.Handler;

public class FutureBooleanImplFast implements FutureBoolean
{
  private final boolean data;


  public FutureBooleanImplFast(boolean data)
  {
    this.data = data;
  }

  @Override
  public void runIfTrue(Runnable runnable)
  {
    if (this.data)
    {
      runnable.run();
    }
  }

  @Override
  public void runIfFalse(Runnable runnable)
  {
    if (!this.data)
    {
      runnable.run();
    }
  }

  @Override
  public boolean getData()
  {
    return this.data;
  }

  @Override
  public boolean isFastFuture()
  {
    return true;
  }

  @Override
  public FutureBooleanImplFast onComplete(Handler<AsyncResult<Boolean>> handler)
  {
    handler.handle(new AsyncResult<Boolean>()
    {
      @Override
      public Boolean result()
      {
        return this.result();
      }

      @Override
      public Throwable cause()
      {
        return null;
      }

      @Override
      public boolean succeeded()
      {
        return true;
      }

      @Override
      public boolean failed()
      {
        return false;
      }
    });
    return this;
  }


  @Override
  public FutureBooleanImplFast onComplete(Handler<AsyncResult<Boolean>> handler, long timeout)
  {
    //the result is there, we dont need to do more complex logic here
    return onComplete(handler);
  }


}
