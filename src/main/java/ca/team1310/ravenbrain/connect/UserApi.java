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
    return userService
        .getUser(id)
        .map(userService::redactPassword)
        .orElseThrow(
            () ->
                new io.micronaut.http.exceptions.HttpStatusException(
                    io.micronaut.http.HttpStatus.NOT_FOUND, "User not found"));
  }

  @Post
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public User create(@Body User user, Authentication authentication) {
    if (user.roles().contains("ROLE_SUPERUSER")) {
      if (!authentication.getRoles().contains("ROLE_SUPERUSER")) {
        throw new io.micronaut.http.exceptions.HttpStatusException(
            io.micronaut.http.HttpStatus.FORBIDDEN, "Only superuser can create superuser");
      }
    }
    if (user.roles().contains("ROLE_ADMIN")) {
      if (!authentication.getRoles().contains("ROLE_SUPERUSER")) {
        throw new io.micronaut.http.exceptions.HttpStatusException(
            io.micronaut.http.HttpStatus.FORBIDDEN, "Only admin or superuser can create admin");
      }
    }
    return userService.redactPassword(userService.createUser(user));
  }

  @Put("/{id}")
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public User update(long id, @Body User user, Authentication authentication) {
    if (!authentication.getRoles().contains("ROLE_SUPERUSER")) {
      User existingUser =
          userService
              .getUser(id)
              .orElseThrow(
                  () ->
                      new io.micronaut.http.exceptions.HttpStatusException(
                          io.micronaut.http.HttpStatus.NOT_FOUND, "User not found"));

      if (user.roles().contains("ROLE_SUPERUSER")
          && !existingUser.roles().contains("ROLE_SUPERUSER")) {
        throw new io.micronaut.http.exceptions.HttpStatusException(
            io.micronaut.http.HttpStatus.FORBIDDEN, "Only superuser can add ROLE_SUPERUSER");
      }
      if (user.roles().contains("ROLE_ADMIN") && !existingUser.roles().contains("ROLE_ADMIN")) {
        throw new io.micronaut.http.exceptions.HttpStatusException(
            io.micronaut.http.HttpStatus.FORBIDDEN, "Only superuser can add ROLE_ADMIN");
      }
    }
    return userService.redactPassword(userService.updateUser(id, user));
  }

  @Delete("/{id}")
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public void delete(long id) {
    userService.deleteUser(id);
  }

  @Post("/forgot-password")
  @Secured(SecurityRule.IS_AUTHENTICATED)
  public void forgotPassword(Authentication authentication) {
    userService.notePasswordForgotten(authentication.getName());
  }

  @Get("/forgot-password")
  @Secured({"ROLE_ADMIN", "ROLE_SUPERUSER"})
  public List<User> listForgotPassword() {
    return userService.listUsersWithForgotPassword().stream()
        .map(userService::redactPassword)
        .toList();
  }
}
