package ca.team1310.ravenbrain.tbaapi.service;

import ca.team1310.ravenbrain.tbaapi.model.TbaMatchVideo;
import ca.team1310.ravenbrain.tbaapi.model.TbaWebcast;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Converts TBA {@code {type, channel}} webcast tuples into {@code https://} URLs and canonicalizes
 * URLs for duplicate detection.
 *
 * <p>This class is the single source of URL normalization in the system — both sync-time
 * persistence of {@code RB_TBA_EVENT.webcasts_json} (Unit 4) and read-time merge with manual
 * overrides (Unit 5) must call {@link #canonicalize(String)} through this class so sync-side and
 * read-side de-duplication stay in lockstep.
 *
 * <p>Only four TBA webcast types are expressed as URLs in P0: {@code youtube}, {@code twitch},
 * {@code livestream}, {@code direct_link}. Other types (iframe HTML embeds, html5/rtmp/dacast
 * stream URLs that the existing RavenEye {@code safeHref()} validator would reject, and
 * effectively-dead services like ustream/justin/stemtv/mms) are dropped with a debug log. Coverage
 * can be extended when the strat team reports a concrete missing stream.
 */
@Slf4j
public final class WebcastUrlReconstructor {

  private WebcastUrlReconstructor() {}

  /**
   * Reconstruct an {@code https://} URL from a TBA webcast entry. Returns empty for unsupported
   * types or malformed input.
   */
  public static Optional<String> reconstruct(TbaWebcast webcast) {
    if (webcast == null) return Optional.empty();
    String type = webcast.type();
    String channel = webcast.channel();
    if (type == null || channel == null || channel.isBlank()) {
      return Optional.empty();
    }

    String url =
        switch (type) {
          case "youtube" -> "https://www.youtube.com/watch?v=" + channel;
          case "twitch" -> "https://www.twitch.tv/" + channel;
          case "livestream" -> "https://livestream.com/accounts/" + channel;
          case "direct_link" -> channel;
          default -> null;
        };

    if (url == null) {
      log.debug("Dropping unsupported TBA webcast type '{}' (channel={})", type, channel);
      return Optional.empty();
    }
    return Optional.of(url);
  }

  /**
   * Canonicalize a URL so duplicates differing only in case, default port, or trailing slash
   * collapse to a single entry under string equality. Preserves query exactly — some stream
   * platforms use query parameters as part of identity.
   *
   * <p>On any URL that cannot be parsed (including non-http(s) schemes), returns the input
   * unchanged rather than throwing — the merged webcast list is best-effort and a malformed URL
   * will simply be compared verbatim.
   */
  public static String canonicalize(String url) {
    if (url == null) return null;
    String trimmed = url.trim();
    if (trimmed.isEmpty()) return trimmed;
    try {
      URI parsed = new URI(trimmed);
      String scheme = parsed.getScheme();
      String host = parsed.getHost();
      if (scheme == null || host == null) {
        return trimmed;
      }
      String lowerScheme = scheme.toLowerCase();
      String lowerHost = host.toLowerCase();
      int port = parsed.getPort();
      boolean isDefaultPort =
          port == -1
              || ("http".equals(lowerScheme) && port == 80)
              || ("https".equals(lowerScheme) && port == 443);
      String path = parsed.getRawPath();
      if (path == null) path = "";
      if (path.endsWith("/")) {
        // Strip trailing slash on ALL paths, including bare "/" — example.com and example.com/
        // are semantically equivalent and must canonicalize to the same string.
        path = path.substring(0, path.length() - 1);
      }
      StringBuilder rebuilt = new StringBuilder();
      rebuilt.append(lowerScheme).append("://").append(lowerHost);
      if (!isDefaultPort) {
        rebuilt.append(":").append(port);
      }
      rebuilt.append(path);
      if (parsed.getRawQuery() != null) {
        rebuilt.append("?").append(parsed.getRawQuery());
      }
      if (parsed.getRawFragment() != null) {
        rebuilt.append("#").append(parsed.getRawFragment());
      }
      return rebuilt.toString();
    } catch (URISyntaxException e) {
      return trimmed;
    }
  }

  /**
   * Reconstruct + canonicalize an entire TBA webcasts list, preserving first-seen order and
   * dropping intra-payload duplicates (TBA sometimes returns the same channel twice with different
   * {@code date} values — P0 ignores {@code date}, so the duplicates must collapse).
   */
  public static List<String> reconstructAndDedup(List<TbaWebcast> webcasts) {
    if (webcasts == null || webcasts.isEmpty()) return List.of();
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    for (TbaWebcast w : webcasts) {
      reconstruct(w).map(WebcastUrlReconstructor::canonicalize).ifPresent(seen::add);
    }
    return new ArrayList<>(seen);
  }

  /**
   * Reconstruct an {@code https://} URL from a TBA Match video entry. Only two types carry usable
   * URLs: {@code youtube} (reconstructed from the video key) and {@code tba} (points at TBA's own
   * hosted match page — the video's {@code key} field is ignored; the enclosing match key is used
   * instead). Other types ({@code iframe}, {@code html5}, {@code rtmp}, etc.) are dropped with a
   * debug log — same philosophy as webcast reconstruction.
   */
  public static Optional<String> reconstructMatchVideo(TbaMatchVideo video, String tbaMatchKey) {
    if (video == null) return Optional.empty();
    String type = video.type();
    if (type == null) {
      return Optional.empty();
    }

    return switch (type) {
      case "youtube" -> {
        String key = video.key();
        if (key == null || key.isBlank()) {
          yield Optional.empty();
        }
        yield Optional.of("https://www.youtube.com/watch?v=" + key.trim());
      }
      case "tba" -> {
        if (tbaMatchKey == null || tbaMatchKey.isBlank()) {
          yield Optional.empty();
        }
        yield Optional.of("https://www.thebluealliance.com/match/" + tbaMatchKey.trim());
      }
      default -> {
        log.debug("Dropping unsupported TBA match video type '{}'", type);
        yield Optional.empty();
      }
    };
  }

  /**
   * Reconstruct + canonicalize an entire TBA match videos list, preserving first-seen order and
   * dropping intra-payload duplicates.
   */
  public static List<String> reconstructAndDedupMatchVideos(
      List<TbaMatchVideo> videos, String tbaMatchKey) {
    if (videos == null || videos.isEmpty()) return List.of();
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    for (TbaMatchVideo v : videos) {
      reconstructMatchVideo(v, tbaMatchKey)
          .map(WebcastUrlReconstructor::canonicalize)
          .ifPresent(seen::add);
    }
    return new ArrayList<>(seen);
  }
}
