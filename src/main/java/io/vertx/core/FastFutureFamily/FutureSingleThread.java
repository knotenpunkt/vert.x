package io.vertx.core.FastFutureFamily;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.impl.ContextInternal;


/**
 *
 * This method here is my shown method here in the google-groups, i renamed it from FastFuture to FutureSingleThread
 *
 * This future here can be used in situations, where at promiseobject.future()-time,
 * the result/error is avialable. Moreover the future must be used on the same thread/context, cause of
 * visibility/locking-purposes
 *
 */
public class FutureSingleThread<T> implements Promise<T>, Future<T>
{
	private T result;
	private Throwable error;

  /**
   * if i use that constructor, it should be the same as a succeededFuture/failedFuture
   *
   */
	public FutureSingleThread( T result, Throwable error)
	{
    //assert -> (result !=null && error != null) && one of them must be null
		this.result = result;
		this.error = error;
	}

	public FutureSingleThread()
	{

	}


	@Override
	public ContextInternal context()
	{
	  throw new RuntimeException("not avialable");
	}


	@Override
	public boolean isComplete()
	{
		return true;
	}

	@Override
	public Future<T> onComplete(Handler<AsyncResult<T>> handler)
	{
    //no context-switch needed, cause that future must only be used on the same thread/context
    //furthermore it is completed at the time onComplete is called
		handler.handle(this);
		return this;
	}

  @Override
  public Future<T> onComplete(Handler<AsyncResult<T>> handler, long timeout)
  {
    //the result is still there^^
    return onComplete(handler);
  }


	@Override
	public Handler<AsyncResult<T>> getHandler()
	{
		return null;
	}

	@Override
	public T result()
	{
		return result;
	}

	@Override
	public Throwable cause()
	{
		return error;
	}

	@Override
	public boolean succeeded()
	{
		return error == null;
	}

	@Override
	public boolean failed()
	{
		return error != null;
	}

	@Override
	public boolean tryComplete(T result)
	{
		if (this.error != null || this.result != null) return false;
		this.result = result;
		return true;
	}

	@Override
	public boolean tryFail(Throwable cause)
	{
		if (this.error != null || this.result != null) return false;
		this.error = cause;
		return true;
	}

	@Override
	public Future<T> future()
	{
		return this;
	}
}
