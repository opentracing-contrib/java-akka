/*
 * Copyright 2017-2018 The OpenTracing Authors
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
package io.opentracing.akka;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import akka.dispatch.OnComplete;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.ThreadLocalScopeManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import static akka.dispatch.Futures.future;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TracedExecutionContextTest {
    final MockTracer mockTracer = new MockTracer(new ThreadLocalScopeManager(),
        MockTracer.Propagator.TEXT_MAP);

    @Before
    public void before() throws Exception {
        mockTracer.reset();
    }

    @Test(expected=IllegalArgumentException.class)
    public void testIllegalContext() throws Exception {
        new TracedExecutionContext(null, mockTracer, false);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testIllegalTracer() throws Exception {
        new TracedExecutionContext(ExecutionContext.global(), null, false);
    }

    @Test
    public void testPropagation() throws Exception {
        ExecutionContext ec = new TracedExecutionContext(ExecutionContext.global(), mockTracer);
        Future f = null;
        Span span = null;

        try (Scope scope = mockTracer.buildSpan("one").startActive(false)) {
            span = scope.span();

            f = future(new Callable<Span>() {
                @Override
                public Span call() {
                    assertNotNull(mockTracer.scopeManager().active());
                    return mockTracer.scopeManager().active().span();
                }
            }, ec);
        }

        Object result = Await.result(f, TestUtils.getDefaultDuration());
        assertEquals(span, result);
        assertEquals(0, mockTracer.finishedSpans().size());

        span.finish();
        assertEquals(1, mockTracer.finishedSpans().size());
        assertEquals(span, mockTracer.finishedSpans().get(0));
    }

    @Test
    public void testCreateSpans() throws Exception {
        ExecutionContext ec = new TracedExecutionContext(ExecutionContext.global(), mockTracer, true);
        Future f = null;
        Span parentSpan = null;

        try (Scope scope = mockTracer.buildSpan("parent").startActive(false)) {
            parentSpan = scope.span();

            f = future(new Callable<Span>() {
                @Override
                public Span call() {
                    assertNotNull(mockTracer.scopeManager().active());
                    return mockTracer.scopeManager().active().span();
                }
            }, ec);
        }

        Span span = (Span)Await.result(f, TestUtils.getDefaultDuration());
        await().atMost(TestUtils.DEFAULT_CALLBACK_SYNC_TIMEOUT, TimeUnit.SECONDS)
                .until(TestUtils.finishedSpansSize(mockTracer), equalTo(1));
        assertEquals(1, mockTracer.finishedSpans().size());

        parentSpan.finish();
        List<MockSpan> finishedSpans = mockTracer.finishedSpans();
        assertEquals(2, finishedSpans.size());
        assertEquals(parentSpan, finishedSpans.get(1));
        assertEquals(span, finishedSpans.get(0));
        assertEquals(finishedSpans.get(1).context().spanId(), finishedSpans.get(0).parentId());
    }

    @Test
    public void testNoActiveSpan() throws Exception {
        ExecutionContext ec = new TracedExecutionContext(ExecutionContext.global(), mockTracer);

        Future f = future(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                assertNull(mockTracer.scopeManager().active());
                return mockTracer.scopeManager().active() != null;
            }
        }, ec);

        Boolean isActive = (Boolean)Await.result(f, TestUtils.getDefaultDuration());
        assertFalse(isActive);
        assertEquals(0, mockTracer.finishedSpans().size());
    }

    @Test
    public void testGlobalTracer() throws Exception {
        ExecutionContext ec = new TracedExecutionContext(ExecutionContext.global());

        Future f = future(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                assertNull(mockTracer.scopeManager().active());
                return mockTracer.scopeManager().active() != null;
            }
        }, ec);

        Boolean isActive = (Boolean)Await.result(f, TestUtils.getDefaultDuration());
        assertFalse(isActive);
        assertEquals(0, mockTracer.finishedSpans().size());
    }

    @Test
    public void testConvert() throws Exception {
        ExecutionContext ec = new TracedExecutionContext(ExecutionContext.global(), mockTracer);

        try (Scope scope = mockTracer.buildSpan("one").startActive(false)) {
            future(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    assertNotNull(mockTracer.scopeManager().active());
                    mockTracer.scopeManager().active().span().setTag("main", Boolean.TRUE);
                    return Boolean.TRUE;
                }
            }, ec)
            .andThen(new OnComplete<Boolean>() {
                @Override
                public void onComplete(Throwable failure, Boolean result) {
                    assertNotNull(mockTracer.scopeManager().active());
                    mockTracer.scopeManager().active().span().setTag("interceptor", Boolean.TRUE);
                }
            }, ec)
            .onComplete(new OnComplete<Boolean>() {
                @Override
                public void onComplete(Throwable failure, Boolean result) {
                    assertNotNull(mockTracer.scopeManager().active());
                    mockTracer.scopeManager().active().span().setTag("done", Boolean.TRUE);
                    mockTracer.scopeManager().active().span().finish();
                }
            }, ec);

            await().atMost(TestUtils.DEFAULT_CALLBACK_SYNC_TIMEOUT, TimeUnit.SECONDS)
                .until(TestUtils.finishedSpansSize(mockTracer), equalTo(1));

            List<MockSpan> finishedSpans = mockTracer.finishedSpans();
            assertEquals(1, finishedSpans.size());

            Map<String, ?> tags = finishedSpans.get(0).tags();
            assertEquals(3, tags.size());
            assertTrue(tags.containsKey("main"));
            assertTrue(tags.containsKey("interceptor"));
            assertTrue(tags.containsKey("done"));
        }
    }
}
