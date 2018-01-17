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
