package ca.team1310.ravenbrain.connect;

import ca.team1310.ravenbrain.eventlog.EventLogRepository;
import ca.team1310.ravenbrain.quickcomment.QuickCommentService;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.security.authentication.Authentication;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public class UserService {

  private final UserRepository userRepository;
  private final EventLogRepository eventLogRepository;
  private final QuickCommentService quickCommentService;
  private final RefreshTokenRepository refreshTokenRepository;
  private final Config config;
  private final String seed;

  public UserService(
      UserRepository userRepository,
      EventLogRepository eventLogRepository,
      QuickCommentService quickCommentService,
      RefreshTokenRepository refreshTokenRepository,
      Config config) {
    this.userRepository = userRepository;
    this.eventLogRepository = eventLogRepository;
    this.quickCommentService = quickCommentService;
    this.refreshTokenRepository = refreshTokenRepository;
    this.config = config;
    this.seed = config.encryptionSeed();
  }

  @EventListener
  public void onStartup(StartupEvent event) {

    // reset the superuser password to the prop value
    final String password = config.superuser();
    if (password == null) throw new IllegalStateException("Superuser password is not configured.");

    User u =
        userRepository
            .findByLogin("superuser")
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Superuser not found, but it should always be in the database."));
    User updated =
        new User(
            u.id(),
            u.login(),
            u.displayName(),
            hashPassword(password),
            true,
            false,
            List.of("ROLE_SUPERUSER"));

    userRepository.update(updated);
  }

  public List<User> listUsers() {
    return userRepository.findAll();
  }

  public User getUser(long id) {
    return userRepository
        .findById(id)
        .orElseThrow(() -> new UserNotFoundException("User not found"));
  }

  public User selfRegister(String signupSecretCode, User user) {
    // todo: fixme: implement creating a new account with a secret key
    return user;
  }

  public Optional<User> findByLogin(String username) {
    return userRepository.findByLogin(username);
  }

  public void notePasswordForgotten(String userid) {
    userRepository
        .findByLogin(userid)
        .ifPresent(
            user -> {
              User userToSave =
                  new User(
                      user.id(),
                      user.login(),
                      user.displayName(),
                      user.passwordHash(),
                      user.enabled(),
                      true,
                      user.roles());
              userRepository.update(userToSave);
            });
  }

  public User updateOwnProfile(User update, String login) {
    User existing =
        userRepository
            .findByLogin(login)
            .orElseThrow(() -> new UserNotFoundException("User not found"));

    String passwordToSave;
    boolean passwordChanging =
        update.passwordHash() != null
            && !update.passwordHash().isEmpty()
            && !update.passwordHash().equals("REDACTED");
    if (passwordChanging) {
      passwordToSave = hashPassword(update.passwordHash());
    } else {
      passwordToSave = existing.passwordHash();
    }

    User userToSave =
        new User(
            existing.id(),
            existing.login(),
            update.displayName(),
            passwordToSave,
            existing.enabled(),
            passwordChanging ? false : existing.forgotPassword(),
            existing.roles());

    User saved = userRepository.update(userToSave);
    if (passwordChanging) {
      refreshTokenRepository.updateByUsername(existing.login(), true);
    }
    return saved;
  }

  public User createUser(User user, Authentication caller) {
    Collection<String> callerRoles = caller.getRoles();
    if (user.roles().contains("ROLE_SUPERUSER") && !callerRoles.contains("ROLE_SUPERUSER")) {
      throw new UserForbiddenException("Only superuser can create superuser");
    }
    if (user.roles().contains("ROLE_ADMIN") && !callerRoles.contains("ROLE_SUPERUSER")) {
      throw new UserForbiddenException("Only admin or superuser can create admin");
    }

    Optional<User> existing = userRepository.findByLogin(user.login());
    if (existing.isPresent()) {
      return existing.get();
    }
    String hashedPassword = hashPassword(user.passwordHash());
    User userToSave =
        new User(
            0,
            user.login(),
            user.displayName(),
            hashedPassword,
            user.enabled(),
            user.forgotPassword(),
            user.roles());
    return userRepository.save(userToSave);
  }

  public User updateUser(long id, User user, Authentication caller) {
    User existingUser =
        userRepository
            .findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found"));

    Collection<String> callerRoles = caller.getRoles();
    String callerLogin = caller.getName();
    boolean callerIsSuperuser = callerRoles.contains("ROLE_SUPERUSER");
    boolean callerIsAdmin = callerRoles.contains("ROLE_ADMIN");

    if (!callerIsSuperuser && existingUser.roles().contains("ROLE_SUPERUSER")) {
      throw new UserForbiddenException("Only superuser can modify a superuser");
    }

    if (!callerIsSuperuser
        && user.enabled()
        && !existingUser.enabled()
        && existingUser.roles().contains("ROLE_ADMIN")) {
      throw new UserForbiddenException("Only superuser can enable a disabled admin");
    }

    if (!callerIsSuperuser
        && user.roles().contains("ROLE_SUPERUSER")
        && !existingUser.roles().contains("ROLE_SUPERUSER")) {
      throw new UserForbiddenException("Only superuser can add ROLE_SUPERUSER");
    }

    if (!callerIsSuperuser
        && user.roles().contains("ROLE_ADMIN")
        && !existingUser.roles().contains("ROLE_ADMIN")) {
      throw new UserForbiddenException("Only superuser can add ROLE_ADMIN");
    }

    // Determine if a new plaintext password is being submitted
    boolean passwordChanging =
        user.passwordHash() != null
            && !user.passwordHash().isEmpty()
            && !user.passwordHash().equals("REDACTED");

    if (passwordChanging) {
      // Admins and superusers can always change passwords; otherwise forgotPassword must be set
      boolean allowed = callerIsSuperuser || callerIsAdmin || existingUser.forgotPassword();
      if (!allowed) {
        throw new UserForbiddenException(
            "Cannot change password unless forgot password flag is set");
      }
    }

    // Only admins/superusers can set the forgotPassword flag for other users
    if (user.forgotPassword() && !existingUser.forgotPassword()) {
      if (!callerIsSuperuser && !callerIsAdmin && !callerLogin.equals(existingUser.login())) {
        throw new UserForbiddenException("Cannot set forgot password flag for other users");
      }
    }

    String passwordToSave;
    if (passwordChanging) {
      passwordToSave = hashPassword(user.passwordHash());
    } else {
      passwordToSave = existingUser.passwordHash();
    }

    // Auto-clear forgotPassword flag when the password is changed
    boolean forgotPassword = passwordChanging ? false : user.forgotPassword();

    User userToUpdate =
        new User(
            id,
            user.login(),
            user.displayName(),
            passwordToSave,
            user.enabled(),
            forgotPassword,
            user.roles());

    User saved = userRepository.update(userToUpdate);
    boolean disabling = !user.enabled() && existingUser.enabled();
    if (passwordChanging || disabling) {
      refreshTokenRepository.updateByUsername(existingUser.login(), true);
    }
    return saved;
  }

  public void deleteUser(long id, Authentication caller) {
    Collection<String> callerRoles = caller.getRoles();
    if (!callerRoles.contains("ROLE_SUPERUSER")) {
      User existingUser =
          userRepository
              .findById(id)
              .orElseThrow(() -> new UserNotFoundException("User not found"));

      if (existingUser.roles().contains("ROLE_SUPERUSER")) {
        throw new UserForbiddenException("Only superuser can delete a superuser");
      }
    }

    if (eventLogRepository.existsByUserId(id) || quickCommentService.existsByUserId(id)) {
      throw new UserInUseException("Cannot delete user with recorded events or comments");
    }

    userRepository.deleteById(id);
  }

  public List<User> listUsersWithForgotPassword() {
    return userRepository.findByForgotPasswordTrue();
  }

  String hashPassword(String password) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      String saltedPassword = password + seed;
      byte[] hash = digest.digest(saltedPassword.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Error hashing password", e);
    }
  }

  Map<String, Object> buildClaims(User user) {
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("userid", user.id());
    claims.put("login", user.login());
    claims.put("displayName", user.displayName());
    return claims;
  }

  User redactPassword(User userToRedact) {
    return new User(
        userToRedact.id(),
        userToRedact.login(),
        userToRedact.displayName(),
        "REDACTED",
        userToRedact.enabled(),
        userToRedact.forgotPassword(),
        userToRedact.roles());
  }
}
