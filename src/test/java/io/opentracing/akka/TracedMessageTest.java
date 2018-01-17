package io.opentracing.akka;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.ThreadLocalScopeManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TracedMessageTest {
    final MockTracer mockTracer = new MockTracer(new ThreadLocalScopeManager());

    @Before
    public void before() throws Exception {
        mockTracer.reset();
        GlobalTracer.register(mockTracer);
    }

    @Before
    public void after() throws Exception {
        TestUtils.resetGlobalTracer();
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullMessage() {
        TracedMessage.wrap(null);
    }

    @Test
    public void testExplicitNoActiveSpan() {
        String originalMessage = "foo";
        Object message = TracedMessage.wrap(null, originalMessage);
        assertEquals(message, originalMessage);
    }

    @Test
    public void testImplicitNoActiveSpan() {
        String originalMessage = "foo";
        Object message = TracedMessage.wrap(originalMessage);
        assertEquals(message, originalMessage);
    }

    @Test
    public void testImplicitActiveSpan() {
        String originalMessage = "foo";
        Object message = null;
        Span span = mockTracer.buildSpan("one").start();

        try (Scope scope = mockTracer.scopeManager().activate(span, false)) {
            message = TracedMessage.wrap(originalMessage);
        }
        assertTrue(message instanceof TracedMessage);

        TracedMessage tracedMessage = (TracedMessage)message;
        assertEquals(span, tracedMessage.activeSpan());
        assertEquals(originalMessage, tracedMessage.message());
    }

    @Test
    public void testExplicitActiveSpan() {
        String originalMessage = "foo";
        Span span = mockTracer.buildSpan("one").start();

        Object message = TracedMessage.wrap(span, originalMessage);
        assertTrue(message instanceof TracedMessage);

        TracedMessage tracedMessage = (TracedMessage)message;
        assertEquals(span, tracedMessage.activeSpan());
        assertEquals(originalMessage, tracedMessage.message());
    }
}
