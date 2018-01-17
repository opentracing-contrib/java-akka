/*
 * Copyright 2016-2017 The OpenTracing Authors
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

import io.opentracing.ScopeManager;
import io.opentracing.Span;

import java.util.concurrent.atomic.AtomicInteger;

// Originally imported from
// opentracing-java/opentracing-examples/src/test/java/io/opentracing/examples/
public class AutoFinishScopeManager implements ScopeManager {
    final ThreadLocal<AutoFinishScope> tlsScope = new ThreadLocal<AutoFinishScope>();

    @Override
    public AutoFinishScope activate(Span span, boolean finishOnClose) {
        return new AutoFinishScope(this, new AtomicInteger(1), span);
    }

    @Override
    public AutoFinishScope active() {
        return tlsScope.get();
    }

}
