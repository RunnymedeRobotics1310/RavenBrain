package ca.team1310.ravenbrain.sync;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

@Controller("/api/config-sync")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class ConfigSyncApi {

  private final ConfigSyncService configSyncService;

  ConfigSyncApi(ConfigSyncService configSyncService) {
    this.configSyncService = configSyncService;
  }

  @Post
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public SyncResult sync(@Body SyncRequest request) {
    return configSyncService.syncFromSource(request);
  }
}
