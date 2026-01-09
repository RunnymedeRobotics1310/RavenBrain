package ca.team1310.ravenbrain.connect;

import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Singleton
public class UserService {

  private final UserRepository userRepository;
  private final Config config;
  private final String seed;

  public UserService(UserRepository userRepository, Config config) {
    this.userRepository = userRepository;
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

  public Optional<User> getUser(long id) {
    return userRepository.findById(id);
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

  public User createUser(User user) {
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

  public User updateUser(long id, User user) {
    User existingUser =
        userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));

    String passwordToSave = user.passwordHash();
    if (passwordToSave != null
        && !passwordToSave.isEmpty()
        && !passwordToSave.equals("REDACTED")
        && !passwordToSave.equals(existingUser.passwordHash())) {
      passwordToSave = hashPassword(passwordToSave);
    } else {
      passwordToSave = existingUser.passwordHash();
    }

    User userToUpdate =
        new User(
            id,
            user.login(),
            user.displayName(),
            passwordToSave,
            user.enabled(),
            user.forgotPassword(),
            user.roles());

    return userRepository.update(userToUpdate);
  }

  public void deleteUser(long id) {
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
