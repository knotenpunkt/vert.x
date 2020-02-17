/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.FastFutureFamily;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.core.impl.PromiseInternal;
import io.vertx.core.impl.VertxInternal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FutureImplNormal<T> implements PromiseInternal<T>, Future<T> {

  private final VertxInternal vertx;
  private boolean failed;
  private boolean succeeded;
  //private HandlerWithContext<T> handlerWithContext; //Todo that we can use for the first handler, the list after the first handler (creation and adding)
  private final List<HandlerWithContext<T>> handlerWithContextList= new ArrayList<HandlerWithContext<T>>();
  private T result;
  private Throwable throwable;

  /**
   * Create a future that hasn't completed yet
   */
  public FutureImplNormal(VertxInternal vertx)
  {
    this.vertx = vertx;
  }

  /**
   * The result of the operation. This will be null if the operation failed.
   */
  public synchronized T result() {
    return result;
  }

  /**
   * An exception describing failure. This will be null if the operation succeeded.
   */
  public synchronized Throwable cause() {
    return throwable;
  }

  /**
   * Did it succeeed?
   */
  public synchronized boolean succeeded() {
    return succeeded;
  }

  /**
   * Did it fail?
   */
  public synchronized boolean failed() {
    return failed;
  }

  /**
   * Has it completed?
   */
  public synchronized boolean isComplete() {
    return failed || succeeded;
  }

  @Override
  public Future<T> onComplete(Handler<AsyncResult<T>> h) {
    Objects.requireNonNull(h, "No null handler accepted");

    if (!isComplete())
    {
      HandlerWithContext<T> handlerWithContext = new HandlerWithContext<>(h, vertx.getOrCreateContext());


      synchronized (this)
      {
        if (!isComplete())
        {
          handlerWithContextList.add(handlerWithContext);
          return this;
        }
      }
    }
    vertx.getOrCreateContext().dispatch(this,h);
    return this;
  }

  @Override
  public Future<T> onComplete(Handler<AsyncResult<T>> h, long timeout)
  {
    Objects.requireNonNull(h, "No null handler accepted");

    if (!isComplete())
    {
      ContextInternal callerContext = vertx.getOrCreateContext();
      TimeoutWrapperHandler<T> timeoutWrapperHandler = new TimeoutWrapperHandler<>(h, callerContext, 0);
      HandlerWithContext<T> handlerWithContext = new HandlerWithContext<>(timeoutWrapperHandler, callerContext);

      synchronized (this)
      {
        if (!isComplete())
        {
          handlerWithContextList.add(handlerWithContext);
        }
      }
      timeoutWrapperHandler.startTimeout(timeout);
      return this;
    }
    vertx.getOrCreateContext().dispatch(this,h);
    return this;
  }





  @Override
  public Handler<AsyncResult<T>> getHandler() {
    return null;
  }

  @Override
  public boolean tryComplete(T result) {
    Handler<AsyncResult<T>> h;
    synchronized (this) {
      if (succeeded || failed) {
        return false;
      }
      this.result = result;
      succeeded = true;
//      h = handler;
//      handler = null;
    }

    //here at this moment the handlerList is not anymore manipulated, cause of succed=true
    //-> no copy of the handler/handlerList is neccessary

    for (HandlerWithContext<T> value : handlerWithContextList)
    {
      value.doDispatch(this);
    }
    return true;
  }

  /**
   * for what is that method for?
   */
  public void handle(Future<T> ar) {
    if (ar.succeeded()) {
      complete(ar.result());
    } else {
      fail(ar.cause());
    }
  }

  @Override
  public boolean tryFail(Throwable cause) {
    Handler<AsyncResult<T>> h;
    synchronized (this) {
      if (succeeded || failed) {
        return false;
      }
      this.throwable = cause != null ? cause : new NoStackTraceThrowable(null);
      failed = true;
//      h = handler;
//      handler = null;
    }
    //here at this moment the handlerList is not anymore manipulated, cause of succed=true
    //-> no copy of the handler/handlerList is neccessary

    for (HandlerWithContext<T> value : handlerWithContextList)
    {
      value.doDispatch(this);
    }

    return true;
  }

  @Override
  public Future<T> future() {
    return this;
  }

  /**
   *
   * for what is that method here for?
   * could we use that also in my other futures? to be faster?
   *
   * @param future
   */
  @Override
  public void operationComplete(io.netty.util.concurrent.Future<T> future) {
    if (future.isSuccess()) {
      complete(future.getNow());
    } else {
      fail(future.cause());
    }
  }

  @Override
  public String toString() {
    synchronized (this) {
      if (succeeded) {
        return "Future{result=" + result + "}";
      }
      if (failed) {
        return "Future{cause=" + throwable.getMessage() + "}";
      }
      return "Future{unresolved}";
    }
  }


}
