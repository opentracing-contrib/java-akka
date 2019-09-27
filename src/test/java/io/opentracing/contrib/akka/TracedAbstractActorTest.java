/*
 * Copyright 2017-2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing.contrib.akka;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.util.Timeout;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.GlobalTracerTestUtil;
import io.opentracing.util.ThreadLocalScopeManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import static akka.pattern.Patterns.ask;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TracedAbstractActorTest {

    private final MockTracer mockTracer = new MockTracer(new ThreadLocalScopeManager());
    private ActorSystem system;

    @Before
    public void before() {
        GlobalTracerTestUtil.resetGlobalTracer(); // safe start, just in case.

        mockTracer.reset();
        GlobalTracer.registerIfAbsent(mockTracer);

        system = ActorSystem.create("testSystem");
    }

    @After
    public void after() throws Exception {
        GlobalTracerTestUtil.resetGlobalTracer(); // clean up.

        Await.result(system.terminate(), getDefaultDuration());
    }

    static abstract class TestActor extends TracedAbstractActor {

        public TestActor() {
            super(GlobalTracer.get());
        }

        public TestActor(Tracer tracer) {
            super(tracer);
        }
    }

    static class SpanNullCheckActor extends TestActor {

        public static Props props() {
            return Props.create(SpanNullCheckActor.class, SpanNullCheckActor::new);
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .matchAny(x -> getSender().tell(tracer().scopeManager().activeSpan() == null, getSelf()))
                    .build();
        }
    }

    @Test
    public void testNoActiveSpan() throws Exception {
        ActorRef actorRef = system.actorOf(SpanNullCheckActor.props(), "one");
        Timeout timeout = new Timeout(getDefaultDuration());
        Future<Object> future = ask(actorRef, TracedMessage.wrap("foo"), timeout);

        Boolean isSpanNull = (Boolean) Await.result(future, getDefaultDuration());
        assertTrue(isSpanNull);
    }

    static class SpanCheckActor extends TestActor {

        public static Props props() {
            return Props.create(SpanCheckActor.class, SpanCheckActor::new);
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .matchAny(x -> {
                        Span span = tracer().scopeManager().activeSpan();
                        boolean isSameSpan = span != null && span.equals(x);
                        getSender().tell(isSameSpan, getSelf());
                    })
                    .build();
        }
    }

    @Test
    public void testActiveSpan() throws Exception {
        ActorRef actorRef = system.actorOf(SpanCheckActor.props(), "actorOne");
        Timeout timeout = new Timeout(getDefaultDuration());

        Future<Object> future;
        final MockSpan parent = mockTracer.buildSpan("one").start();
        try (Scope ignored = mockTracer.activateSpan(parent)) {
            Object message = TracedMessage.wrap(mockTracer.activeSpan() /* message */);
            future = ask(actorRef, message, timeout);
        }
        parent.finish();

        Boolean isSpanSame = (Boolean) Await.result(future, getDefaultDuration());
        assertTrue(isSpanSame);
    }

    @Test
    public void testNoWrapMessage() throws Exception {
        ActorRef actorRef = system.actorOf(SpanCheckActor.props(), "actorOne");
        Timeout timeout = new Timeout(getDefaultDuration());

        Future<Object> future;
        final MockSpan parent = mockTracer.buildSpan("one").start();
        try (Scope ignored = mockTracer.activateSpan(parent)) {
            /* Since scope.span() is not TracedMessage, the active Span
             * won't be propagated */
            future = ask(actorRef, mockTracer.activeSpan(), timeout);
        }
        parent.finish();

        Boolean isSpanSame = (Boolean) Await.result(future, getDefaultDuration());
        assertFalse(isSpanSame);
    }

    static class TracerCheckActor extends TestActor {

        public TracerCheckActor() {
            super();
        }

        public TracerCheckActor(Tracer tracer) {
            super(tracer);
        }

        public static Props props() {
            return Props.create(TracerCheckActor.class, () -> new TracerCheckActor());
        }

        public static Props props(Tracer tracer) {
            return Props.create(TracerCheckActor.class, () -> new TracerCheckActor(tracer));
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .matchAny(x -> getSender().tell(tracer() == x, getSelf()))
                    .build();
        }
    }

    @Test
    public void testExplicitTracer() throws Exception {
        ActorRef actorRef = system.actorOf(TracerCheckActor.props(mockTracer), "one");
        Timeout timeout = new Timeout(getDefaultDuration());

        Future<Object> future;
        final MockSpan parent = mockTracer.buildSpan("one").start();
        try (Scope ignored = mockTracer.activateSpan(parent)) {
            future = ask(actorRef, mockTracer, timeout);
        }
        parent.finish();

        Boolean isTracerSame = (Boolean) Await.result(future, getDefaultDuration());
        assertTrue(isTracerSame);
    }

    @Test
    public void testGlobalTracer() throws Exception {
        ActorRef actorRef = system.actorOf(TracerCheckActor.props(), "one");
        Timeout timeout = new Timeout(getDefaultDuration());

        Future<Object> future;
        final MockSpan parent = mockTracer.buildSpan("one").start();
        try (Scope ignored = mockTracer.activateSpan(parent)) {
            future = ask(actorRef, GlobalTracer.get(), timeout);
        }
        parent.finish();

        Boolean isTracerSame = (Boolean) Await.result(future, getDefaultDuration());
        assertTrue(isTracerSame);
    }

    private static FiniteDuration getDefaultDuration() {
        return Duration.create(3, "seconds");
    }
}
