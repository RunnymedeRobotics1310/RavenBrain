package ca.team1310.ravenbrain.telemetry;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;

@Filter("/api/telemetry/**")
@Slf4j
public class TelemetryApiKeyFilter implements HttpServerFilter {

  private static final String API_KEY_HEADER = "X-Telemetry-Key";

  private final String apiKey;

  TelemetryApiKeyFilter(
      @Nullable @Property(name = "raven-eye.telemetry.api-key") String apiKey) {
    this.apiKey = apiKey;
  }

  @Override
  public Publisher<MutableHttpResponse<?>> doFilter(
      HttpRequest<?> request, ServerFilterChain chain) {
    String providedKey = request.getHeaders().get(API_KEY_HEADER);
    if (apiKey == null || apiKey.isBlank()) {
      log.warn("Telemetry API key is not configured, rejecting request");
      return Publishers.just(HttpResponse.unauthorized());
    }
    if (providedKey == null || !providedKey.equals(apiKey)) {
      log.warn("Invalid or missing telemetry API key from {}", request.getRemoteAddress());
      return Publishers.just(HttpResponse.unauthorized());
    }
    return chain.proceed(request);
  }
}
