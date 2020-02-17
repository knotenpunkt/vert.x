package io.vertx.core;

import io.vertx.core.FastFutureFamily.FutureBooleanImplFast;
import io.vertx.core.FastFutureFamily.FutureBooleanImplSlow;

public interface FutureBoolean
{

	static FutureBoolean createFastFutureBoolean(boolean data)
	{
		return new FutureBooleanImplFast(data);
	}

	static FutureBoolean createSlowFutureBoolean(Future<Boolean> data)
	{
		return new FutureBooleanImplSlow(data);
	}

	static FutureBoolean createSlowFutureBoolean(Promise<Boolean> data)
	{
		return new FutureBooleanImplSlow(data.future());
	}


	public void runIfTrue(Runnable runnable);

	public void runIfFalse(Runnable runnable);

	public boolean getData();

	public boolean isFastFuture();

	public FutureBoolean onComplete(Handler<AsyncResult<Boolean>> handler);

	public FutureBoolean onComplete(Handler<AsyncResult<Boolean>> handler, long timeout);

}
