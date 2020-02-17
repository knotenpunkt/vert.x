package io.vertx.core.FastFutureFamily;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.FutureBoolean;
import io.vertx.core.Handler;

public class FutureBooleanImplSlow implements FutureBoolean
{
	private final Future<Boolean> slowFuture;


	public FutureBooleanImplSlow(Future<Boolean> slowFuture)
	{
		this.slowFuture = slowFuture;
	}

	@Override
	public void runIfTrue(Runnable runnable)
	{
		this.slowFuture.onComplete(event ->
		{
			Boolean result = event.result();
			if (result != null && result)
			{
				runnable.run();
			}
		});
	}

	@Override
	public void runIfFalse(Runnable runnable)
	{
		this.slowFuture.onComplete(event ->
		{
			Boolean result = event.result();
			if (result == null || !result)
			{
				runnable.run();
			}
		});
	}

	@Override
	public boolean getData()
	{
		return this.slowFuture.result();
	}

	@Override
	public boolean isFastFuture()
	{
		return false;
	}

	@Override
	public FutureBooleanImplSlow onComplete(Handler<AsyncResult<Boolean>> handler)
	{
		this.slowFuture.onComplete(handler);
		return this;
	}

  @Override
  public FutureBooleanImplSlow onComplete(Handler<AsyncResult<Boolean>> handler, long timeout)
  {
    this.slowFuture.onComplete(handler, timeout);
    return this;
  }



}
