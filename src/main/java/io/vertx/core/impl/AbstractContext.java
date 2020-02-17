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
package io.vertx.core.impl;

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.vertx.core.*;
import io.vertx.core.impl.launcher.VertxCommandLauncher;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static io.vertx.core.impl.VertxThread.DISABLE_TCCL;

/**
 * A context implementation that does not hold any specific state.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
abstract class AbstractContext implements ContextInternal {

  static final String THREAD_CHECKS_PROP_NAME = "vertx.threadChecks";
  static final boolean THREAD_CHECKS = Boolean.getBoolean(THREAD_CHECKS_PROP_NAME);

  static class Holder implements BlockedThreadChecker.Task {

    BlockedThreadChecker checker;
    ContextInternal ctx;
    long startTime = 0;
    long maxExecTime = VertxOptions.DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME;
    TimeUnit maxExecTimeUnit = VertxOptions.DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME_UNIT;

    @Override
    public long startTime() {
      return startTime;
    }

    @Override
    public long maxExecTime() {
      return maxExecTime;
    }

    @Override
    public TimeUnit maxExecTimeUnit() {
      return maxExecTimeUnit;
    }
  }

  final static FastThreadLocal<Holder> holderLocal = new FastThreadLocal<Holder>() {
    @Override
    protected Holder initialValue() {
      return new Holder();
    }
  };

  /**
   * Execute the {@code task} on the context.
   *
   * @param argument the argument for the {@code task}
   * @param task the task to execute with the provided {@code argument}
   */
  abstract <T> void execute(T argument, Handler<T> task);

  @Override
  public abstract boolean isEventLoopContext();

  @Override
  public boolean isWorkerContext() {
    return !isEventLoopContext();
  }

  @Override
  public final <T> void dispatch(T argument, Handler<T> task) {
    schedule(v -> emit(argument, task));
  }

  @Override
  public void dispatch(Handler<Void> task) {
    dispatch(null, task);
  }

  @Override
  public final void schedule(Handler<Void> task) {
    schedule(null, task);
  }

  @Override
  public final void emit(Handler<Void> handler) {
    emit(null, handler);
  }

  public final ContextInternal emitBegin() {
    ContextInternal prev;
    Thread th = Thread.currentThread();
    if (th instanceof VertxThread) {
      prev = ((VertxThread)th).beginEmission(this);
    } else {
      prev = beginNettyThreadEmit(th);
    }
    if (!DISABLE_TCCL) {
      th.setContextClassLoader(classLoader());
    }
    return prev;
  }

  private ContextInternal beginNettyThreadEmit(Thread th) {
    if (th instanceof FastThreadLocalThread) {
      Holder holder = holderLocal.get();
      ContextInternal prev = holder.ctx;
      if (!ContextImpl.DISABLE_TIMINGS) {
        if (holder.checker == null) {
          BlockedThreadChecker checker = owner().blockedThreadChecker();
          holder.checker = checker;
          holder.maxExecTime = owner().maxEventLoopExecTime();
          holder.maxExecTimeUnit = owner().maxEventLoopExecTimeUnit();
          checker.registerThread(th, holder);
        }
        if (holder.ctx == null) {
          holder.startTime = System.nanoTime();
        }
      }
      holder.ctx = this;
      return prev;
    } else {
      throw new IllegalStateException("Uh oh! context executing with wrong thread! " + th);
    }
  }

  public final void emitEnd(ContextInternal previous) {
    Thread th = Thread.currentThread();
    if (!DISABLE_TCCL) {
      th.setContextClassLoader(previous != null ? previous.classLoader() : null);
    }
    if (th instanceof VertxThread) {
      ((VertxThread)th).endEmission(previous);
    } else {
      endNettyThreadAssociation(th, previous);
    }
  }

  @Override
  public long setPeriodic(long delay, Handler<Long> handler) {
    VertxImpl owner = (VertxImpl) owner();
    return owner.scheduleTimeout(this, handler, delay, true);
  }

  @Override
  public long setTimer(long delay, Handler<Long> handler) {
    VertxImpl owner = (VertxImpl) owner();
    return owner.scheduleTimeout(this, handler, delay, false);
  }

  private static void endNettyThreadAssociation(Thread th, ContextInternal prev) {
    if (th instanceof FastThreadLocalThread) {
      Holder holder = holderLocal.get();
      holder.ctx = prev;
      if (!ContextImpl.DISABLE_TIMINGS) {
        if (holder.ctx == null) {
          holder.startTime = 0L;
        }
      }
    } else {
      throw new IllegalStateException("Uh oh! context executing with wrong thread! " + th);
    }
  }

  @Override
  public final <T> void emit(T event, Handler<T> handler) {
    ContextInternal prev = emitBegin();
    try {
      handler.handle(event);
    } catch (Throwable t) {
      reportException(t);
    } finally {
      emitEnd(prev);
    }
  }

  public final void emit(Runnable handler) {
    ContextInternal prev = emitBegin();
    try {
      handler.run();
    } catch (Throwable t) {
      reportException(t);
    } finally {
      emitEnd(prev);
    }
  }

  static void checkEventLoopThread() {
    Thread current = Thread.currentThread();
    if (!(current instanceof FastThreadLocalThread)) {
      throw new IllegalStateException("Expected to be on Vert.x thread, but actually on: " + current);
    } else if ((current instanceof VertxThread) && ((VertxThread) current).isWorker()) {
      throw new IllegalStateException("Event delivered on unexpected worker thread " + current);
    }
  }

  // Run the task asynchronously on this same context
  @Override
  public final void runOnContext(Handler<Void> handler) {
    try {
      execute(null, handler);
    } catch (RejectedExecutionException ignore) {
      // Pool is already shut down
    }
  }

  @Override
  public final List<String> processArgs() {
    // As we are maintaining the launcher and starter class, choose the right one.
    List<String> processArgument = VertxCommandLauncher.getProcessArguments();
    return processArgument != null ? processArgument : Starter.PROCESS_ARGS;
  }

  @Override
  public final <T> void executeBlockingInternal(Handler<Promise<T>> action, Handler<AsyncResult<T>> resultHandler) {
    Future<T> fut = executeBlockingInternal(action);
    setResultHandler(this, fut, resultHandler);
  }

  @Override
  public <T> void executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> resultHandler) {
    Future<T> fut = executeBlocking(blockingCodeHandler, ordered);
    setResultHandler(this, fut, resultHandler);
  }

  @Override
  public <T> void executeBlocking(Handler<Promise<T>> blockingCodeHandler, TaskQueue queue, Handler<AsyncResult<T>> resultHandler) {
    Future<T> fut = executeBlocking(blockingCodeHandler, queue);
    setResultHandler(this, fut, resultHandler);
  }

  @Override
  public final <T> void executeBlocking(Handler<Promise<T>> blockingCodeHandler, Handler<AsyncResult<T>> resultHandler) {
    executeBlocking(blockingCodeHandler, true, resultHandler);
  }

  public <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler) {
    return executeBlocking(blockingCodeHandler, true);
  }

  @Override
  public <T> PromiseInternal<T> promise() {
    return Future.factory.promise(this);
  }

  @Override
  public <T> PromiseInternal<T> promise(Handler<AsyncResult<T>> handler) {
    if (handler instanceof PromiseInternal) {
      return (PromiseInternal<T>) handler;
    } else {
      PromiseInternal<T> promise = promise();
      promise.future().setHandler(handler);
      return promise;
    }
  }

  @Override
  public <T> Future<T> succeededFuture() {
    return Future.factory.succeededFuture(this);
  }

  @Override
  public <T> Future<T> succeededFuture(T result) {
    return Future.factory.succeededFuture(this, result);
  }

  @Override
  public <T> Future<T> failedFuture(Throwable failure) {
    return Future.factory.failedFuture(this, failure);
  }

  @Override
  public <T> Future<T> failedFuture(String message) {
    return Future.factory.failedFuture(this, message);
  }

  @SuppressWarnings("unchecked")
  @Override
  public final <T> T get(String key) {
    return (T) contextData().get(key);
  }

  @Override
  public final void put(String key, Object value) {
    contextData().put(key, value);
  }

  @Override
  public final boolean remove(String key) {
    return contextData().remove(key) != null;
  }

  @SuppressWarnings("unchecked")
  @Override
  public final <T> T getLocal(String key) {
    return (T) localContextData().get(key);
  }

  @Override
  public final void putLocal(String key, Object value) {
    localContextData().put(key, value);
  }

  @Override
  public final boolean removeLocal(String key) {
    return localContextData().remove(key) != null;
  }

  private static <T> void setResultHandler(ContextInternal ctx, Future<T> fut, Handler<AsyncResult<T>> resultHandler) {
    if (resultHandler != null) {
      fut.setHandler(resultHandler);
    } else {
      fut.setHandler(ar -> {
        if (ar.failed()) {
          ctx.reportException(ar.cause());
        }
      });
    }
  }

  @Override
  public boolean checkCtxtEquality()
  {
    return this == this.owner().getOrCreateContext();
  }

  /**
   * runnAbleWithReturn muss einen Rueckgabewert haben, der im Falle der direkten Ausfuehrung
   * dann direkt an ein succeeded/failed Future weitergegeben wird
   * <p>
   * bei async-ausfuehrung bzw. bei equalityCheck=false wird ein an context bounded promise/future zurueckgegeben
   */
  @Override
  public <T> Future<T> runAndGetFuture(boolean equalityCheck, Supplier<T> runnableWithReturn)
  {

    ContextInternal callerContextS = owner().getOrCreateContext();

    if (equalityCheck && callerContextS == this)
    {
      try
      {
        return Future.succeededFuture(runnableWithReturn.get());
      }
      catch (Throwable t)
      {
        return Future.failedFuture(t);
      }
    }

    PromiseInternal<T> promise = callerContextS.promise();
    this.runOnContext(h ->
    {
      try
      {
        promise.complete(runnableWithReturn.get());//complete is executed on the callerContext, cause of context binding to promise
      }
      catch (Throwable t)
      {
        promise.tryFail(t);
      }
    });

    return promise.future();
  }


  /**
   * runnable erhaelt ein Promise. Dieses muss bei isFastFutureAllowed=true hierbei vor Methodenende completed oder failed/exceptioniert worden sein
   * Denn in diesem Fall kann bei direkter Ausfuehrung ein FastFuture zurueckgegeben werden
   * Bei isFastFutureAllowed=false wird ein normales context bounded promise/future zurueckgegeben.
   * <p>
   * <p>
   * bei async-ausfuehrung bzw. bei equalityCheck=false wird immer ein an context bounded promise/future zurueckgegeben
   */
  @Override
  public <T> Future<T> runAndGetFuture(boolean equalityCheck, boolean isFastFutureAllowed, Handler<Promise<T>> runnable)
  {
    ContextInternal callerContextS = owner().getOrCreateContext();

    if (equalityCheck && callerContextS == this)
    {
      //callerContext binding deshalb, weil wenn das promise weitergereicht wird, dass dann sichergestellt wird,
      //dass bei der rueckgabe das promise im richtigen context aufgerufen wird
      Promise<T> promiseFast = (isFastFutureAllowed) ? Promise.promiseSingleThread(/*callerContextS*/) : callerContextS.promise();

      try
      {
        runnable.handle(promiseFast);
        //da es die gleichen threads sind -> callerContext==ressourceContext
      }
      catch (Throwable t)
      {
        promiseFast.tryFail(t);
      }
      return promiseFast.future();
    }

    PromiseInternal<T> promise = callerContextS.promise();
    this.runOnContext(h ->
    {
      try
      {
        runnable.handle(promise);
        //complete is executed on the callerContext, cause of context binding to promise
      }
      catch (Throwable t)
      {
        promise.tryFail(t);
      }
    });

    return promise.future();
  }


  private ContextInternal getCallerContextOrNullS()
  {
    ContextInternal callerContext = this.owner().getOrCreateContext();
    return (callerContext == this) ? null : callerContext;
  }


  /**
   * aehnlich zu runOnContext. prueft aber vorab noch (bei equalityCheck=true), ob ein context-switch ueberhaupt notwendig ist
   */
  @Override
  public <T> void run(boolean equalityCheck, Runnable ressourceRunner)
  {
    ContextInternal callerContext = getCallerContextOrNullS();

    if (equalityCheck && callerContext == null)
    {
      try
      {
        ressourceRunner.run();
      }
      catch (Exception e)
      {
        callerContext.reportException(e);
      }
      return;
    }

    this.runOnContext(h ->
    {
      ressourceRunner.run();
    });
  }



  /**
   * ressourceRunner erhaelt ein Promise. Dieses muss bei isFastFutureAllowed=true hierbei vor Methodenende completed oder failed/exceptioniert worden sein
   * Denn in diesem Fall kann bei direkter Ausfuehrung ein FastFuture verwendet
   * Bei isFastFutureAllowed=false wird ein normales context bounded promise/future verwendet.
   * <p>
   * Im Falle der direkten Ausfuehrung wird entsprechendes future oder fastFuture direkt an callerRunner uebergeben
   * <p>
   * Im Falle der asynchronen Ausfuehrung wird ebenso entweder ein FastFuture oder normales Future verwendet, das auch direkt an callerRunner uebergeben wird
   */
  @Override
  public <T> void run(boolean equalityCheck, boolean isFastFutureAllowed, Handler<Promise<T>> ressourceRunner, Handler<AsyncResult<T>> callerRunner)
  {
    ContextInternal callerContextS = owner().getOrCreateContext();

    if (equalityCheck && callerContextS == this)
    {
      //callerContext binding deshalb, weil wenn das promise weitergereicht wird, dass dann sichergestellt wird,
      //dass bei der rueckgabe das promise im richtigen context aufgerufen wird
      Promise<T> promiseFast = (isFastFutureAllowed) ? Promise.promiseSingleThread(/*callerContextS*/) : callerContextS.promise();
      try
      {
        ressourceRunner.handle(promiseFast);
      }
      catch (Throwable t)
      {
        promiseFast.tryFail(t);
      }
      this.runHelper(isFastFutureAllowed, promiseFast, callerRunner);
      return;
    }

    this.runOnContext(h ->
    {
      //callerContext binding deshalb, weil wenn das promise weitergereicht wird, dass dann sichergestellt wird,
      //dass bei der rueckgabe das promise im richtigen context aufgerufen wird
      Promise<T> promiseFast = (isFastFutureAllowed) ? Promise.promiseSingleThread(/*callerContextS*/) : callerContextS.promise();
      try
      {
        ressourceRunner.handle(promiseFast); //nur hier duerfte ne exception fliegen
        callerContextS.runOnContext(h2 ->
        {
          this.runHelper(isFastFutureAllowed, promiseFast, callerRunner);
        });
      }
      catch (Throwable t)
      {
        promiseFast.tryFail(t);
        callerContextS.runOnContext(h2 ->
        {
          this.runHelper(isFastFutureAllowed, promiseFast, callerRunner);
        });
      }
    });
  }

  /**
   * helper for runS: entscheidet ob das future direkt an asyncresult uebergeben werden darf, oder ob es mit onComplete (hier setHandler) auf das Ergebnis warten muss
   */
  private <T> void runHelper(boolean isFastFutureAllowed, Promise<T> promiseFast, Handler<AsyncResult<T>> callerRunner)
  {
    if (isFastFutureAllowed)
    {
      //direct call
      callerRunner.handle(promiseFast.future());
    }
    else
    {
      //wait on complete call
      promiseFast.future().setHandler(callerRunner);
    }
  }


  /**
   * ressourceRunner muss ein Rueckgabewert haben, der dann direkt an ein Succeded im Falle einer Exception an ein Failed Future weitergeben wird.
   * Dieses wiederum wird dann sofort dem callerRunner uebergeben
   */
  @Override
  public <T> void run(boolean equalityCheck, Supplier<T> ressourceRunner, Handler<AsyncResult<T>> callerRunner)
  {
    ContextInternal callerContextS = owner().getOrCreateContext();

    if (equalityCheck && callerContextS == this)
    {
      Future<T> tmp;
      try
      {
        tmp = Future.succeededFuture(ressourceRunner.get());
      }
      catch (Throwable t)
      {
        tmp = Future.failedFuture(t);
      }

      callerRunner.handle(tmp);
      return;
    }


    this.runOnContext(h ->
    {
      try
      {
        T back = ressourceRunner.get(); //nur hier duerfte ne exception fliegen
        callerContextS.runOnContext(h2 ->
        {
          Future<T> tmp = Future.succeededFuture(back);
          callerRunner.handle(tmp);
        });
      }
      catch (Throwable t)
      {
        callerContextS.runOnContext(h2 ->
        {
          Future<T> tmp = Future.failedFuture(t);
          callerRunner.handle(tmp);
        });
      }
    });
  }


  /**
   * RessourceRunner muss einen Rueckgabewert haben, das im dann direkt im BiConsumer callerRunner landet, das zweite Argument steht fuer error und ist in diesem Fall null
   * Sollte der RessourceRunner eine Exception werfen so ist das erste Argument null und error enthaelt/erhaelt die Exception
   */
  @Override
  public <T> void run(boolean equalityCheck, Supplier<T> ressourceRunner, BiConsumer<T, Throwable> callerRunner)
  {
    ContextInternal callerContextS = owner().getOrCreateContext();

    if (equalityCheck && callerContextS == this)
    {
      Throwable error = null;
      T result = null;
      try
      {
        result = ressourceRunner.get();
      }
      catch (Exception e)
      {
        result = null;
        error = e;
      }

      try
      {
        callerRunner.accept(result, error);
      }
      catch (Throwable t)
      {
        callerContextS.reportException(t);
      }

      return;
    }

    this.runOnContext(h ->
    {
      try
      {
        T back = ressourceRunner.get(); //nur hier duerfte ne exception fliegen
        callerContextS.runOnContext(h2 ->
        {
          callerRunner.accept(back, null);
        });
      }
      catch (Exception e)
      {
        callerContextS.runOnContext(h2 ->
        {
          callerRunner.accept(null, e);
        });
      }
    });
  }


}
