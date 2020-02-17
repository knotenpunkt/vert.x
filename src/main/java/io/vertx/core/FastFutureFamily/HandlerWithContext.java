package io.vertx.core.FastFutureFamily;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;

public class HandlerWithContext<T>
  {
    public final Handler<AsyncResult<T>> handler;
    public final ContextInternal context;

    public HandlerWithContext(Handler<AsyncResult<T>> handler, ContextInternal context)
    {
      this.handler = handler;
      this.context = context;
    }

    //Todo change public to package-private, after the normal future is in that package here too
    public void doDispatch(Future<T> parent)
    {
      if (this.context != null)
      {
        this.context.dispatch(parent, this.handler);
      }
      else
      {
        this.handler.handle(parent);
      }
    }


  }
