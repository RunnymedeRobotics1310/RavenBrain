package ca.team1310.ravenbrain.connect;

import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import java.util.List;

@Controller("/api/users")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class UserApi {

  private final UserService userService;

  public UserApi(UserService userService) {
    this.userService = userService;
  }

  @Get
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public List<User> list() {
    return userService.listUsers().stream().map(userService::redactPassword).toList();
  }

  @Get("/{id}")
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public User get(long id) {
    return userService.redactPassword(userService.getUser(id));
  }

  @Post
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public User create(@Body User user, Authentication authentication) {
    return userService.redactPassword(userService.createUser(user, authentication));
  }

  @Put("/{id}")
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public User update(long id, @Body User user, Authentication authentication) {
    return userService.redactPassword(userService.updateUser(id, user, authentication));
  }

  @Delete("/{id}")
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public void delete(long id, Authentication authentication) {
    userService.deleteUser(id, authentication);
  }

  @Post("/forgot-password")
  @Secured(SecurityRule.IS_ANONYMOUS)
  public void forgotPassword(@QueryValue String login) {
    userService.notePasswordForgotten(login);
  }

  @Get("/forgot-password")
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public List<User> listForgotPassword() {
    return userService.listUsersWithForgotPassword().stream()
        .map(userService::redactPassword)
        .toList();
  }
}
