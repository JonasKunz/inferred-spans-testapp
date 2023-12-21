package co.elastic.test;

import co.elastic.otel.profiler.InferredSpansProcessor;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;

import java.time.Duration;

public class InferredSpansTestApp {

    private static final String APM_SERVER_URL = "http://localhost:8200";
    private static final String SECRET_TOKEN = "t0gn2MD950zZwD97CDIJ178d";

    public static void main(String[] args) {
        //OpenTelemetry sdk = manualSdkNoInferredSpans();
        //OpenTelemetry sdk = manualSdkWithInferredSpans();
        OpenTelemetry sdk = autoconfiguredSdk();
        while (true) {
            doTracing(sdk.getTracer("my-manual-tracer"));
            doSleep(1000);
        }
    }


    private static OpenTelemetrySdk manualSdkWithInferredSpans() {
        //Set up our inferred-spans extension
        InferredSpansProcessor profiler = InferredSpansProcessor.builder()
                .samplingInterval(Duration.ofMillis(10))
                //.backupDiagnosticFiles(true)
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(Resource.builder()
                        .put(ResourceAttributes.SERVICE_NAME, "Service with manual SDK")
                        .build())
                // register our extension
                .addSpanProcessor(profiler)
                .addSpanProcessor(BatchSpanProcessor.builder(OtlpGrpcSpanExporter.builder()
                                .setEndpoint(APM_SERVER_URL)
                                .addHeader("Authorization", "Bearer " + SECRET_TOKEN)
                                .build()
                        )
                        .build())
                .build();
        profiler.setTracerProvider(tracerProvider);

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
    }


    private static OpenTelemetry autoconfiguredSdk() {

        System.setProperty("otel.java.global-autoconfigure.enabled", "true");
        System.setProperty("otel.service.name", "Service with autoconfigured SDK");
        System.setProperty("otel.exporter.otlp.endpoint", APM_SERVER_URL);
        System.setProperty("otel.exporter.otlp.headers", "Authorization=Bearer " + SECRET_TOKEN);

        //Set up our inferred-spans extension
        System.setProperty("elastic.profiling.inferred.spans.enabled", "true");
        System.setProperty("elastic.profiling.inferred.spans.sampling.interval", "10ms");

        return GlobalOpenTelemetry.get();
    }


    private static OpenTelemetrySdk manualSdkNoInferredSpans() {
        Resource resource = Resource.builder()
                .put(ResourceAttributes.SERVICE_NAME, "Service without Inferred Spans")
                .build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(OtlpGrpcSpanExporter.builder()
                                .setEndpoint(APM_SERVER_URL)
                                .addHeader("Authorization", "Bearer " + SECRET_TOKEN)
                                .build()
                        )
                        .build())
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
    }

    private static void doTracing(Tracer tracer) {
        Span span = tracer.spanBuilder("my-root").startSpan();
        try (var scope = span.makeCurrent()) {
            doSleep(100);
            inferMeParent(tracer);
        } finally {
            span.end();
        }
    }

    private static void inferMeParent(Tracer tracer) {
        inferMe(tracer);
    }

    private static void inferMe(Tracer tracer) {
        child1(tracer);
        inferMeToo();
        child2(tracer);
    }

    private static void child1(Tracer tracer) {
        Span span1 = tracer.spanBuilder("child-1").startSpan();
        try (var scope = span1.makeCurrent()) {
            doSleep(200);
        } finally {
            span1.end();
        }
    }

    private static void child2(Tracer tracer) {
        Span span2 = tracer.spanBuilder("child-2").startSpan();
        try (var scope = span2.makeCurrent()) {
            doSleep(400);
        } finally {
            span2.end();
        }
    }


    private static void inferMeToo() {
        doSleep(300);
    }

    private static void doSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
