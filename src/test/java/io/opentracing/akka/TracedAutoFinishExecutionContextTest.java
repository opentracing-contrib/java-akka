package io.opentracing.akka;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;

import akka.dispatch.Mapper;
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
import static akka.dispatch.Futures.sequence;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class TracedAutoFinishExecutionContextTest {
    final MockTracer mockTracer = new MockTracer(new AutoFinishScopeManager(),
        MockTracer.Propagator.TEXT_MAP);

    @Before
    public void before() throws Exception {
        mockTracer.reset();
    }

    @Test(expected=IllegalArgumentException.class)
    public void testIllegalContext() throws Exception {
        new TracedAutoFinishExecutionContext(null, mockTracer);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testIllegalTracer() throws Exception {
        new TracedAutoFinishExecutionContext(ExecutionContext.global(), null);
    }

    @Test
    public void testSimple() throws Exception {
        ExecutionContext ec = new TracedAutoFinishExecutionContext(ExecutionContext.global(), mockTracer);
        Future f = null;
        Span span = null;

        try (Scope scope = mockTracer.buildSpan("one").startActive(true)) {
            span = scope.span();

            f = future(new Callable<Span>() {
                @Override
                public Span call() {
                    assertNotNull(mockTracer.scopeManager().active());

                    Span activeSpan = mockTracer.scopeManager().active().span();
                    activeSpan.setTag("done", Boolean.TRUE);
                    return activeSpan;
                }
            }, ec);
        }

        Object result = Await.result(f, TestUtils.getDefaultDuration());
        await().atMost(TestUtils.DEFAULT_CALLBACK_SYNC_TIMEOUT, TimeUnit.SECONDS)
                .until(TestUtils.finishedSpansSize(mockTracer), equalTo(1));
        assertEquals(span, result);
        assertEquals(1, mockTracer.finishedSpans().size());

        MockSpan mockSpan = mockTracer.finishedSpans().get(0);
        assertEquals("one", mockSpan.operationName());
        assertEquals(Boolean.TRUE, mockSpan.tags().get("done"));
    }

    @Test
    public void testMultiple() throws Exception {
        ExecutionContext ec = new TracedAutoFinishExecutionContext(ExecutionContext.global(), mockTracer);
        List<Future<Span>> futures = new LinkedList<Future<Span>>();
        Random rand = new Random();

        try (Scope scope = mockTracer.buildSpan("one").startActive(true)) {

            for (int i = 0; i < 5; i++) {
                futures.add(future(new Callable<Span>() {
                    @Override
                    public Span call() {
                        int sleepMs = rand.nextInt(500);
                        try { Thread.sleep(sleepMs); } catch (InterruptedException e) {}

                        Span activeSpan = mockTracer.scopeManager().active().span();
                        assertNotNull(activeSpan);
                        activeSpan.setTag(Integer.toString(sleepMs), Boolean.TRUE);
                        return activeSpan;
                    }
                }, ec));
            }
        }

        Await.result(sequence(futures, ExecutionContext.global()), TestUtils.getDefaultDuration());
        await().atMost(TestUtils.DEFAULT_CALLBACK_SYNC_TIMEOUT, TimeUnit.SECONDS)
                .until(TestUtils.finishedSpansSize(mockTracer), equalTo(1));
        assertEquals(1, mockTracer.finishedSpans().size());
        assertEquals(5, mockTracer.finishedSpans().get(0).tags().size());
    }

    @Test
    public void testPipeline() throws Exception {
        ExecutionContext ec = new TracedAutoFinishExecutionContext(ExecutionContext.global(), mockTracer);
        Future f = null;

        try (Scope scope = mockTracer.buildSpan("one").startActive(true)) {
            f = future(new Callable<Future>() {
                @Override
                public Future call() {
                    assertNotNull(mockTracer.scopeManager().active());
                    mockTracer.scopeManager().active().span().setTag("1", Boolean.TRUE);

                    return future(new Callable<Boolean>() {
                        @Override
                        public Boolean call() {
                            assertNotNull(mockTracer.scopeManager().active());
                            mockTracer.scopeManager().active().span().setTag("2", Boolean.TRUE);
                            return true;
                        }
                    }, ec);
                }
            }, ec);
        }

        Future f2 = (Future)Await.result(f, TestUtils.getDefaultDuration());
        Await.result(f2, TestUtils.getDefaultDuration());
        await().atMost(TestUtils.DEFAULT_CALLBACK_SYNC_TIMEOUT, TimeUnit.SECONDS)
                .until(TestUtils.finishedSpansSize(mockTracer), equalTo(1));
        assertEquals(1, mockTracer.finishedSpans().size());

        MockSpan mockSpan = mockTracer.finishedSpans().get(0);
        assertEquals("one", mockSpan.operationName());
        assertEquals(2, mockSpan.tags().size());
        assertEquals(Boolean.TRUE, mockSpan.tags().get("1"));
        assertEquals(Boolean.TRUE, mockSpan.tags().get("2"));
    }

    @Test
    public void testConvert() throws Exception {
        ExecutionContext ec = new TracedAutoFinishExecutionContext(ExecutionContext.global(), mockTracer);
        Future f = null;

        try (Scope scope = mockTracer.buildSpan("one").startActive(true)) {
            f = future(new Callable<Integer>() {
                @Override
                public Integer call() {
                    int result = 1099;
                    mockTracer.scopeManager().active().span().setTag("before.map", result);
                    return result;
                }
            }, ec)
            .map(new Mapper<Integer, String>() {
                @Override
                public String apply(Integer n) {
                    String result = n.toString();
                    mockTracer.scopeManager().active().span().setTag("after.map", result);
                    return result;
                }
            }, ec)
            .andThen(new OnComplete<String>() {
                @Override
                public void onComplete(Throwable failure, String s) {
                    Boolean error = failure == null ? Boolean.FALSE : Boolean.TRUE;
                    mockTracer.scopeManager().active().span().setTag("error", error);
                }
            }, ec);
        }

        Await.result(f, TestUtils.getDefaultDuration());
        await().atMost(TestUtils.DEFAULT_CALLBACK_SYNC_TIMEOUT, TimeUnit.SECONDS)
                .until(TestUtils.finishedSpansSize(mockTracer), equalTo(1));
        assertEquals(1, mockTracer.finishedSpans().size());
        assertEquals(3, mockTracer.finishedSpans().get(0).tags().size());
    }
}
