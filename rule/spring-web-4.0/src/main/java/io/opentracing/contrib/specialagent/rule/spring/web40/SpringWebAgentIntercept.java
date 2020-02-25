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

package io.opentracing.contrib.specialagent.rule.spring.web40;

import static io.opentracing.contrib.specialagent.rule.spring.web40.copied.TracingListenableFutureCallback.*;

import io.opentracing.contrib.specialagent.LocalSpanContext;
import java.net.URI;

import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.client.AsyncRequestCallback;
import org.springframework.web.client.RestTemplate;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.common.WrapperProxy;
import io.opentracing.contrib.specialagent.rule.spring.web40.copied.TracingAsyncRequestCallback;
import io.opentracing.contrib.specialagent.rule.spring.web40.copied.TracingListenableFuture;
import io.opentracing.contrib.specialagent.rule.spring.web40.copied.TracingListenableFutureCallback;
import io.opentracing.contrib.specialagent.rule.spring.web40.copied.TracingRestTemplateInterceptor;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class SpringWebAgentIntercept {

  public static void enter(final Object thiz) {
    final RestTemplate restTemplate = (RestTemplate)thiz;
    for (final ClientHttpRequestInterceptor interceptor : restTemplate.getInterceptors())
      if (interceptor instanceof TracingRestTemplateInterceptor)
        return;

    restTemplate.getInterceptors().add(new TracingRestTemplateInterceptor(GlobalTracer.get()));
  }

  public static Object asyncStart(final Object arg0, final Object arg1, final Object arg2) {
    final URI url = (URI)arg0;
    final HttpMethod method = (HttpMethod)arg1;
    final AsyncRequestCallback requestCallback = (AsyncRequestCallback)arg2;

    final Tracer tracer = GlobalTracer.get();
    final Span span = tracer
      .buildSpan(method.name())
      .withTag(Tags.COMPONENT.getKey(), "java-spring-rest-template")
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
      .withTag(Tags.HTTP_URL, url.toString())
      .withTag(Tags.HTTP_METHOD, method.name()).start();

    final Scope scope = tracer.activateSpan(span);
    LocalSpanContext.set(span, scope);

    return WrapperProxy.wrap(requestCallback, new TracingAsyncRequestCallback(requestCallback, span.context()));
  }

  public static Object asyncEnd(final Object response, final Throwable thrown) {
    final LocalSpanContext context = LocalSpanContext.get();
    if (context == null)
      return response;

    if (thrown != null) {
      captureException(context.getSpan(), thrown);
      context.closeAndFinish();
      return response;
    }

    final ListenableFuture<?> listenableFuture = (ListenableFuture<?>)response;
    try {
      listenableFuture.addCallback(new TracingListenableFutureCallback(null, context.getSpan(), true));
    }
    catch (final Exception ignore) {
    }

    LocalSpanContext.remove();
    return WrapperProxy.wrap(listenableFuture, new TracingListenableFuture(listenableFuture, context.getSpan()));
  }
}