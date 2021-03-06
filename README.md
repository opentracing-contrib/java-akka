[![Build Status][ci-img]][ci] [![Coverage Status][cov-img]][cov] [![Released Version][maven-img]][maven] [![Apache-2.0 license](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# OpenTracing Akka Instrumentation
OpenTracing instrumentation for Akka.

## Installation

pom.xml
```xml
<dependency>
    <groupId>io.opentracing.contrib</groupId>
    <artifactId>opentracing-akka</artifactId>
    <version>VERSION</version>
</dependency>
```

## Usage

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

## License

[Apache 2.0 License](./LICENSE).

[ci-img]: https://travis-ci.org/opentracing-contrib/java-akka.svg?branch=master
[ci]: https://travis-ci.org/opentracing-contrib/java-akka
[cov-img]: https://coveralls.io/repos/github/opentracing-contrib/java-akka/badge.svg?branch=master
[cov]: https://coveralls.io/github/opentracing-contrib/java-akka?branch=master
[maven-img]: https://img.shields.io/maven-central/v/io.opentracing.contrib/opentracing-akka.svg
[maven]: http://search.maven.org/#search%7Cga%7C1%7Copentracing-akka
