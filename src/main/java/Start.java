import io.vertx.core.*;
import io.vertx.core.FastFutureFamily.LockedFuture;


public class Start
{
  public static void main(String[] args)
  {
    new Start().start();
  }

  Vertx vertx = Vertx.vertx();
  Context ressourceContext = vertx.getOrCreateContext();


  private void start()
  {
    Context callerContext = vertx.getOrCreateContext();

    //run our user code on the caller-context (it could be an Verticle for instance)
    callerContext.runOnContext(h ->
    {
      Handler<AsyncResult<Long>> handler = x ->
      {
        //doing some stuff
      };




      apiExample1(vertx.promiseLockedOneHandler(handler)); //for example  vertx.createHttpServer().listen(promise)
      apiExample1(handler); //for example  vertx.createHttpServer().listen(handler), already exists, but must be refactored
      Future<Long> result = apiExample1(null); //for example vertx.createHttpServer().listen(), already exists, but must be refactored

      result.onComplete(handler);//if i am on the same eventloop, i get a promiseSingleThreadFuture -> no synchronized in it
      //if i am on an other eventloop, i get a NormalFuture, which is the old vertx-future, which have an slow synchronized in it



      LockedFuture<Long> futureLockedMultiHandler = vertx.futureLockedMultiHandler();
      futureLockedMultiHandler.onComplete(handler);
      //futureLockedMultiHandler.onComplete(handler2);//i commented this out, cuase i didnt create a handler2
      //and so on

      //after i created the promise, its not anymore allowed to change the handlers
      //cause of that guarantee, i dont need internal a synchronized
      Promise<Long> promise = futureLockedMultiHandler.promise();
      apiExample1(promise); //for example  vertx.createHttpServer().listen(promise)

      //TODO futureLockedOneHandler


      //so now i have created a few more runOnContext-Methods:
      //they are more for the public/User-apps, than for internal stuff

      //i use there an supplier, as getting the result
      Future<Long> res1=ressourceContext.runAndGetFuture(true, () ->
      {
        //doing some stuff on ressourceContext, and get the future, its on the right context
        return 5l;
      });

      //here a promise
      //if is fastFutureAllowed=true -> the secound parameter, then the method must complete the promise before it ends
      //if it is not true, it uses intern a "slower" Promise, but we can then complete the promise, when we want, for example async in an other thread
      Future<Long> res2=ressourceContext.runAndGetFuture(true, true, p ->
      {
        //doing some stuff on ressourceContext, and get the future, its on the right context
        p.complete(5l);
      });

      //dont push to the mpsc-queue if the context is the same, and the first parameter is true
      //if the first parameter is false -> then its the same as the normal runOnContext
      ressourceContext.run(true, ()->{});

      //is not yet implemented, but if it would be, then the following would be true:
      //it would change the context, but only push to the mpsce-queue, if currentEventloop != ressourceEventloop
      //so it would be little bit faster than the normal runOnContext but also a little bit more unfair than the normal runOnContext()
      ressourceContext.runOnContextButDontPushToMpscQueueIfNotNeccessery(xy->{});


      //the following ressourceContext.run-Methods are mostly faster than the runAndGetFuture
      //cause at the getFuture, i could get a FutureNormal back, which have synchronized in it
      //yes i know the supplier-methods there are a problem to the codegen of vertx, we have to refactored it

      //no supplier
      ressourceContext.<Long>run(true, true, h5->{h5.complete(5l);}, h6 ->{long myResult=h6.result();});

      //with supplier
      ressourceContext.<Long>run(true, ()->{return 5l;}, h8 -> {long myResult=h8.result();});

      //with supplier
      ressourceContext.<Long>run(true, ()->{return 5l;}, (bR,bE)->{long myResult=bR;});




    });


    System.out.println("its running!^^");
  }

  private void apiExample1(Handler<AsyncResult<Long>> handler)
  {
    apiExample1(vertx.promiseLockedOneHandler(handler));
  }

  private Future<Long> apiExample1(Promise<Long> promiseLockedOneHandler)
  {
    if (vertx.getOrCreateContext() == ressourceContext)
    {
      if (promiseLockedOneHandler == null)
      {
        //here we could also use Future.succeededFuture, but sometimes i use a generic api
        //who wants to get a promise, and so my promisesingleThread is a good solution
        promiseLockedOneHandler = vertx.promiseSingleThread();
      }

      Long result = 5l;
      promiseLockedOneHandler.complete(result);
      //here i could use return Future.succeedFuture(result);, but in other apis its easier go complete
      //an promise-ghost which is more or less a succededFuture

      return promiseLockedOneHandler.future();
    }


    if (promiseLockedOneHandler == null)
    {
      promiseLockedOneHandler = vertx.futureNormal();
    }

    final Promise<Long> resultPromise = promiseLockedOneHandler;//cause of the lambda, which wants a final promise^^

    ressourceContext.runOnContext(h ->
    {
      //do some stuff and calculate
      Long result = 5l;
      resultPromise.complete(result);
    });

    return resultPromise.future();

  }


}
