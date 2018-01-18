[![Build Status][ci-img]][ci]

# OpenTracing Akka Instrumentation
OpenTracing instrumentation for Akka.

## Usage

Please see the examples directory. Overall, an `ExecutionContext` is wrapped
so the active Span can be captured and activated for a given Scala `Future`.

Create a `TracedExecutionContext` wrapping the actually used `ExecutionContext`,
and pass it around when creating `Future`s:

```java
// Instantiate tracer
Tracer tracer = ...
ExecutionContext ec = new TracedExecutionContext(executionContext, tracer);
```

### Span Propagation

```java
future(new Callable<String>() {
    @Override
    public String call() {
	// The active Span at Future creation time, if any,
	// will be captured and restored here.
        tracer.scopeManager().active().setTag("status.code", getStatusCode());
    }
}, ec);
```

`Future.onComplete` and other `Future` methods will
capture too *any* active `Span` by the time they were registered, so you have
to make sure that both happened under the same active `Span`/`Scope` for this
to work smoothly.

`Span` lifetime handling is not done at the `TracedExecutionContext`,
and hence explicit calls to `Span.finish()` must be put in place - usually
either in the last `Future`/message block or in a `onComplete` callback
function:

```java
future(new Callable<String>() {
    ...
}, ec)
.onComplete(new OnComplete<String>{
    @Override
    public void onComplete(Throwable t, String s) {
        tracer.scopeManager().active().span().finish();
    }
}, ec);
```


### Auto finish Span handling

Span auto-finish is supported through a reference-count system using the specific
`AutoFinishScopeManager` -which needs to be provided at `Tracer` creation time-,
along with using `TracedAutoFinishExecutionContext`:

```java
ScopeManager scopeManager = new AutoFinishScopeManager();
Tracer tracer = ... // Use the created scopeManager here.
ExecutionContext ec = new TracedAutoFinishExecutionContext(executionContext, tracer);
...
try (Scope scope = tracer.buildSpan("request").startActive()) {
    future(new Callable<String>() {
	// Span will be reactivated here
	...
	future(new Callable<String>() {
	    // Span will be reactivated here as well.
            // By the time this future is done,
            // the Span will be automatically finished.
	}, ec);
    }, ec)
} 
```

Reference count for `Span`s is set to 1 at creation, and is increased when
registering `onComplete`, `andThen`, `map`, and similar
`Future` methods - and is decreased upon having such function/callback executed:

```java
future(new Callable<String>() {
    ...
}, ec)
.map(new Mapper<String, String>() {
    ...
}, ec)
.onComplete(new OnComplete<String>() {
    // No need to call `Span.finish()` here at all, as
    // lifetime handling is done implicitly.
    ...
}, ec);
```

### Actor's Span propagation (experimental design)

There's an experimental design and support for propagating `Span`s between `Actor`s (without any lifetime
handling, which means the user is responsible for finishing the `Span`s). For this to work, classes must
inherit from `TracedAbstractActor` instead of `AbstractActor`, and messages must be wrapped using
`TracedMessage.wrap()`:

```java
class MyActor extends TracedAbstractActor {
   @Override
   public Receive createReceive() {
       return receiveBuilder()
           .match(String.class, msg -> {
                // the Span 'foo' will be active for this block,
                // and will NOT be finished upon deactivation.
                getSender().tell("ciao", getSelf());
            })
           .build();
    }
}

try (Scope scope = tracer.buildSpan("foo").startActive()) {
    // scope.span() will be captured as part of TracedMessage.wrap(),
    // and MyActor will receive the original 'myMessageObj` instance.
    Future<String> f = ask(myActorRef, TracedMessage.wrap("hello"), timeout);
    ...
}
```

By default, `TracedAbstractActor`/`TracedMessage` use `io.opentracing.util.GlobalTracer`
to activate and fetch the `Span` respectively, but it's possible to manually specify
both the `Tracer` used to activate and the captured `Span`:

```java
class MyActor extends TracedAbstractActor {
   public Receive createReceive() {
       return receiveBuilder()
           .match(String.class, msg -> {
                // TracedAbstractActor.tracer() returns the Tracer being used,
                // either GlobalTracer or the explicit set one.
                if (tracer().activeSpan() != null) {
                   // Use the active Span, to set tags, create children, finish it, etc.
                   tracer().activeSpan.finish();
                }
                ...
            })
           .build();
   }
}

Span span = tracer.buildSpan("foo").start();
Future<String> f = ask(myActorRef, TracedMessage.wrap(span, "hello"), timeout);
```

  [ci-img]: https://travis-ci.org/opentracing-contrib/java-akka.svg?branch=master
