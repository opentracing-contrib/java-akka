package io.opentracing.akka;

import java.util.concurrent.Callable;
import java.lang.reflect.Field;

import akka.util.Timeout;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;

public final class TestUtils {
    private TestUtils() {}

    public final static int DEFAULT_TIMEOUT = 3;
    public final static int DEFAULT_CALLBACK_SYNC_TIMEOUT = 1;

    public static FiniteDuration getDefaultDuration() {
        return Duration.create(DEFAULT_TIMEOUT, "seconds");
    }

    public static Timeout getDefaultTimeout() {
        return new Timeout(getDefaultDuration());
    }

    public static Callable<Integer> finishedSpansSize(MockTracer tracer) {
        return new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return tracer.finishedSpans().size();
            }
        };
    }

    /* Copied from opentracing-java/opentracing-util/src/test/java/io/opentracing/util/GlobalTracerTestUtil.java */
    public static void resetGlobalTracer() {
        try {
            Field globalTracerField = GlobalTracer.class.getDeclaredField("tracer");
            globalTracerField.setAccessible(true);
            globalTracerField.set(null, NoopTracerFactory.create());
            globalTracerField.setAccessible(false);
        } catch (Exception e) {
            throw new IllegalStateException("Error reflecting GlobalTracer.tracer: " + e.getMessage(), e);
        }
    }
}
