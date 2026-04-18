package ca.team1310.ravenbrain.tbaapi.service;

import static org.junit.jupiter.api.Assertions.*;

import ca.team1310.ravenbrain.tbaapi.model.TbaWebcast;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class WebcastUrlReconstructorTest {

  private static TbaWebcast wc(String type, String channel) {
    return new TbaWebcast(type, channel, null, null);
  }

  @Test
  void reconstruct_youtube() {
    assertEquals(
        Optional.of("https://www.youtube.com/watch?v=abc123"),
        WebcastUrlReconstructor.reconstruct(wc("youtube", "abc123")));
  }

  @Test
  void reconstruct_twitch() {
    assertEquals(
        Optional.of("https://www.twitch.tv/firstinspires"),
        WebcastUrlReconstructor.reconstruct(wc("twitch", "firstinspires")));
  }

  @Test
  void reconstruct_livestream() {
    assertEquals(
        Optional.of("https://livestream.com/accounts/12345"),
        WebcastUrlReconstructor.reconstruct(wc("livestream", "12345")));
  }

  @Test
  void reconstruct_directLink_passesThroughAsUrl() {
    assertEquals(
        Optional.of("https://example.com/stream?k=v"),
        WebcastUrlReconstructor.reconstruct(wc("direct_link", "https://example.com/stream?k=v")));
  }

  @Test
  void reconstruct_unsupportedTypesReturnEmpty() {
    for (String t :
        List.of(
            "iframe", "html5", "rtmp", "dacast", "ustream", "justin", "stemtv", "mms", "unknown")) {
      assertTrue(
          WebcastUrlReconstructor.reconstruct(wc(t, "anything")).isEmpty(),
          "expected empty for unsupported type: " + t);
    }
  }

  @Test
  void reconstruct_nullOrEmptyChannelReturnsEmpty() {
    assertTrue(WebcastUrlReconstructor.reconstruct(wc("youtube", null)).isEmpty());
    assertTrue(WebcastUrlReconstructor.reconstruct(wc("youtube", "")).isEmpty());
    assertTrue(WebcastUrlReconstructor.reconstruct(wc("youtube", "   ")).isEmpty());
  }

  @Test
  void reconstruct_nullWebcastReturnsEmpty() {
    assertTrue(WebcastUrlReconstructor.reconstruct(null).isEmpty());
  }

  @Test
  void canonicalize_lowercasesSchemeAndHost() {
    assertEquals(
        "https://www.twitch.tv/firstinspires",
        WebcastUrlReconstructor.canonicalize("HTTPS://WWW.TWITCH.TV/firstinspires"));
  }

  @Test
  void canonicalize_stripsTrailingSlashFromPath() {
    assertEquals(
        "https://www.twitch.tv/firstinspires",
        WebcastUrlReconstructor.canonicalize("https://www.twitch.tv/firstinspires/"));
  }

  @Test
  void canonicalize_stripsRootPathSlash() {
    // example.com and example.com/ are equivalent — both must canonicalize to the same string.
    assertEquals(
        "https://example.com", WebcastUrlReconstructor.canonicalize("https://example.com/"));
    assertEquals(
        "https://example.com", WebcastUrlReconstructor.canonicalize("https://example.com"));
  }

  @Test
  void canonicalize_stripsDefaultPorts() {
    assertEquals(
        "https://example.com/a",
        WebcastUrlReconstructor.canonicalize("https://example.com:443/a"));
    assertEquals(
        "http://example.com/a", WebcastUrlReconstructor.canonicalize("http://example.com:80/a"));
  }

  @Test
  void canonicalize_preservesNonDefaultPorts() {
    assertEquals(
        "https://example.com:8443/a",
        WebcastUrlReconstructor.canonicalize("https://example.com:8443/a"));
  }

  @Test
  void canonicalize_preservesQueryExactly() {
    // Query parameters can carry identity on some platforms — must survive verbatim.
    assertEquals(
        "https://www.youtube.com/watch?v=AbC_123",
        WebcastUrlReconstructor.canonicalize("https://www.youtube.com/watch?v=AbC_123"));
  }

  @Test
  void canonicalize_unparseableUrlReturnedUnchanged() {
    // Empty path + no scheme — best-effort: return the input, let read-time merge treat as-is.
    assertEquals("not a url", WebcastUrlReconstructor.canonicalize("not a url"));
  }

  @Test
  void canonicalize_nullReturnsNull() {
    assertNull(WebcastUrlReconstructor.canonicalize(null));
  }

  @Test
  void reconstructAndDedup_collapsesRepeatedChannels() {
    // TBA sometimes returns the same channel twice with different `date` values; after
    // reconstruction + canonicalization they must collapse to one entry.
    List<TbaWebcast> payload =
        List.of(
            new TbaWebcast("youtube", "abc123", "2026-03-20", null),
            new TbaWebcast("youtube", "abc123", "2026-03-21", null),
            new TbaWebcast("twitch", "firstinspires", null, null));

    List<String> result = WebcastUrlReconstructor.reconstructAndDedup(payload);

    assertEquals(
        List.of(
            "https://www.youtube.com/watch?v=abc123",
            "https://www.twitch.tv/firstinspires"),
        result);
  }

  @Test
  void reconstructAndDedup_dropsUnsupportedTypes() {
    List<TbaWebcast> payload =
        List.of(
            new TbaWebcast("iframe", "<html>embed</html>", null, null),
            new TbaWebcast("youtube", "abc123", null, null),
            new TbaWebcast("html5", "rtmp://foo", null, null));

    List<String> result = WebcastUrlReconstructor.reconstructAndDedup(payload);

    assertEquals(List.of("https://www.youtube.com/watch?v=abc123"), result);
  }

  @Test
  void reconstructAndDedup_emptyAndNullInput() {
    assertEquals(List.of(), WebcastUrlReconstructor.reconstructAndDedup(null));
    assertEquals(List.of(), WebcastUrlReconstructor.reconstructAndDedup(List.of()));
  }

  @Test
  void reconstructAndDedup_preservesFirstSeenOrder() {
    List<TbaWebcast> payload =
        List.of(
            new TbaWebcast("twitch", "bbb", null, null),
            new TbaWebcast("youtube", "aaa", null, null),
            new TbaWebcast("twitch", "ccc", null, null));

    List<String> result = WebcastUrlReconstructor.reconstructAndDedup(payload);

    assertEquals(
        List.of(
            "https://www.twitch.tv/bbb",
            "https://www.youtube.com/watch?v=aaa",
            "https://www.twitch.tv/ccc"),
        result);
  }
}
