package io.vertx.core.FastFutureFamily;

import io.vertx.core.Future;
import io.vertx.core.Promise;

public interface LockedFuture<T> extends Future<T>
{
  Promise<T> promise();
}
