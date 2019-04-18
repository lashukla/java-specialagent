/* Copyright 2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentracing.contrib.specialagent.concurrent;

import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.concurrent.Callable;

public class TracedCallable<V> implements Callable<V> {
  private final Callable<V> delegate;
  private final SpanContext parentContext;

  public TracedCallable(Callable<V> delegate, SpanContext parentContext) {
    this.delegate = delegate;
    this.parentContext = parentContext;
  }

  @Override
  public V call() throws Exception {
    Span span = GlobalTracer.get().buildSpan("callable").withTag(Tags.COMPONENT, "java-concurrent")
        .addReference(References.FOLLOWS_FROM, parentContext).start();
    try (final Scope scope = GlobalTracer.get().activateSpan(span)) {
      return delegate.call();
    } finally {
      span.finish();
    }
  }
}
