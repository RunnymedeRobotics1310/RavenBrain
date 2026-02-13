package ca.team1310.ravenbrain.connect;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import ca.team1310.ravenbrain.eventlog.EventLogRepository;
import ca.team1310.ravenbrain.quickcomment.QuickCommentService;
import io.micronaut.security.authentication.Authentication;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock UserRepository userRepository;
  @Mock EventLogRepository eventLogRepository;
  @Mock QuickCommentService quickCommentService;

  UserService userService;

  private static final String SEED = "test-seed";

  @BeforeEach
  void setUp() {
    Config.Security security = new Config.Security(SEED, "superpass", "regsecret");
    Config config = new Config("1310", security);
    userService = new UserService(userRepository, eventLogRepository, quickCommentService, config);
  }

  private Authentication authWith(String name, String... roles) {
    return Authentication.build(name, List.of(roles));
  }

  private User member(long id, String login, String passwordHash) {
    return new User(id, login, login, passwordHash, true, false, List.of("ROLE_MEMBER"));
  }

  // --- updateUser: password hashing ---

  @Test
  void updateUserHashesPlaintextPassword() {
    User existing = member(1, "scout", "oldhash");
    when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(userRepository.update(any(User.class))).thenAnswer(i -> i.getArgument(0));

    Authentication admin = authWith("admin", "ROLE_ADMIN");
    User request = new User(1, "scout", "scout", "newPlaintext", true, false, List.of("ROLE_MEMBER"));

    User result = userService.updateUser(1, request, admin);

    String expectedHash = userService.hashPassword("newPlaintext");
    assertEquals(expectedHash, result.passwordHash());
    assertNotEquals("newPlaintext", result.passwordHash());
  }

  @Test
  void updateUserPreservesExistingHashWhenPasswordIsRedacted() {
    User existing = new User(1, "scout", "scout", "existingHash", true, false, List.of("ROLE_MEMBER"));
    when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(userRepository.update(any(User.class))).thenAnswer(i -> i.getArgument(0));

    Authentication admin = authWith("admin", "ROLE_ADMIN");
    User request = new User(1, "scout", "scout", "REDACTED", true, false, List.of("ROLE_MEMBER"));

    User result = userService.updateUser(1, request, admin);

    assertEquals("existingHash", result.passwordHash());
  }

  @Test
  void updateUserPreservesExistingHashWhenPasswordIsNull() {
    User existing = new User(1, "scout", "scout", "existingHash", true, false, List.of("ROLE_MEMBER"));
    when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(userRepository.update(any(User.class))).thenAnswer(i -> i.getArgument(0));

    Authentication admin = authWith("admin", "ROLE_ADMIN");
    User request = new User(1, "scout", "scout", null, true, false, List.of("ROLE_MEMBER"));

    User result = userService.updateUser(1, request, admin);

    assertEquals("existingHash", result.passwordHash());
  }

  // --- updateUser: password change authorization ---

  @Test
  void superuserCanChangePasswordWithoutForgotFlag() {
    User existing = member(1, "scout", "oldhash");
    when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(userRepository.update(any(User.class))).thenAnswer(i -> i.getArgument(0));

    Authentication superuser = authWith("superuser", "ROLE_SUPERUSER");
    User request = new User(1, "scout", "scout", "newPass", true, false, List.of("ROLE_MEMBER"));

    User result = userService.updateUser(1, request, superuser);

    assertEquals(userService.hashPassword("newPass"), result.passwordHash());
  }

  @Test
  void adminCanChangePasswordWithoutForgotFlag() {
    User existing = member(1, "scout", "oldhash");
    when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(userRepository.update(any(User.class))).thenAnswer(i -> i.getArgument(0));

    Authentication admin = authWith("admin", "ROLE_ADMIN");
    User request = new User(1, "scout", "scout", "newPass", true, false, List.of("ROLE_MEMBER"));

    User result = userService.updateUser(1, request, admin);

    assertEquals(userService.hashPassword("newPass"), result.passwordHash());
  }

  @Test
  void memberCannotChangePasswordWithoutForgotFlag() {
    User existing = member(1, "scout", "oldhash");
    when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

    Authentication member = authWith("scout", "ROLE_MEMBER");
    User request = new User(1, "scout", "scout", "newPass", true, false, List.of("ROLE_MEMBER"));

    UserForbiddenException e =
        assertThrows(
            UserForbiddenException.class,
            () -> userService.updateUser(1, request, member));
    assertTrue(e.getMessage().contains("forgot password"));
  }

  @Test
  void memberCanChangePasswordWhenForgotFlagIsSet() {
    User existing =
        new User(1, "scout", "scout", "oldhash", true, true, List.of("ROLE_MEMBER"));
    when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(userRepository.update(any(User.class))).thenAnswer(i -> i.getArgument(0));

    Authentication member = authWith("scout", "ROLE_MEMBER");
    User request = new User(1, "scout", "scout", "newPass", true, true, List.of("ROLE_MEMBER"));

    User result = userService.updateUser(1, request, member);

    assertEquals(userService.hashPassword("newPass"), result.passwordHash());
  }

  // --- updateUser: forgotPassword auto-clear ---

  @Test
  void forgotPasswordFlagClearedWhenPasswordChanged() {
    User existing =
        new User(1, "scout", "scout", "oldhash", true, true, List.of("ROLE_MEMBER"));
    when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(userRepository.update(any(User.class))).thenAnswer(i -> i.getArgument(0));

    Authentication admin = authWith("admin", "ROLE_ADMIN");
    User request = new User(1, "scout", "scout", "newPass", true, true, List.of("ROLE_MEMBER"));

    User result = userService.updateUser(1, request, admin);

    assertFalse(result.forgotPassword());
  }

  @Test
  void forgotPasswordFlagPreservedWhenPasswordNotChanged() {
    User existing =
        new User(1, "scout", "scout", "oldhash", true, true, List.of("ROLE_MEMBER"));
    when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(userRepository.update(any(User.class))).thenAnswer(i -> i.getArgument(0));

    Authentication admin = authWith("admin", "ROLE_ADMIN");
    User request = new User(1, "scout", "scout", "REDACTED", true, true, List.of("ROLE_MEMBER"));

    User result = userService.updateUser(1, request, admin);

    assertTrue(result.forgotPassword());
  }

  // --- updateUser: role escalation ---

  @Test
  void adminCannotAddAdminRole() {
    User existing = member(1, "scout", "hash");
    when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

    Authentication admin = authWith("admin", "ROLE_ADMIN");
    User request =
        new User(1, "scout", "scout", "REDACTED", true, false, List.of("ROLE_MEMBER", "ROLE_ADMIN"));

    assertThrows(
        UserForbiddenException.class, () -> userService.updateUser(1, request, admin));
  }

  @Test
  void adminCannotAddSuperuserRole() {
    User existing = member(1, "scout", "hash");
    when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

    Authentication admin = authWith("admin", "ROLE_ADMIN");
    User request =
        new User(
            1, "scout", "scout", "REDACTED", true, false, List.of("ROLE_MEMBER", "ROLE_SUPERUSER"));

    assertThrows(
        UserForbiddenException.class, () -> userService.updateUser(1, request, admin));
  }

  @Test
  void superuserCanAddAdminRole() {
    User existing = member(1, "scout", "hash");
    when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(userRepository.update(any(User.class))).thenAnswer(i -> i.getArgument(0));

    Authentication superuser = authWith("superuser", "ROLE_SUPERUSER");
    User request =
        new User(1, "scout", "scout", "REDACTED", true, false, List.of("ROLE_MEMBER", "ROLE_ADMIN"));

    User result = userService.updateUser(1, request, superuser);

    assertTrue(result.roles().contains("ROLE_ADMIN"));
  }

  @Test
  void adminCannotModifySuperuser() {
    User existing =
        new User(1, "superuser", "superuser", "hash", true, false, List.of("ROLE_SUPERUSER"));
    when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

    Authentication admin = authWith("admin", "ROLE_ADMIN");
    User request =
        new User(1, "superuser", "Hacked", "REDACTED", true, false, List.of("ROLE_SUPERUSER"));

    assertThrows(
        UserForbiddenException.class, () -> userService.updateUser(1, request, admin));
  }

  // --- createUser: password hashing ---

  @Test
  void createUserHashesPassword() {
    when(userRepository.findByLogin("newscout")).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(i -> {
      User u = i.getArgument(0);
      return new User(99, u.login(), u.displayName(), u.passwordHash(), u.enabled(), u.forgotPassword(), u.roles());
    });

    Authentication superuser = authWith("superuser", "ROLE_SUPERUSER");
    User request = new User(0, "newscout", "New Scout", "plaintext", true, false, List.of("ROLE_MEMBER"));

    User result = userService.createUser(request, superuser);

    assertEquals(userService.hashPassword("plaintext"), result.passwordHash());
    assertNotEquals("plaintext", result.passwordHash());
  }

  // --- createUser: role restrictions ---

  @Test
  void adminCannotCreateSuperuser() {
    Authentication admin = authWith("admin", "ROLE_ADMIN");
    User request =
        new User(0, "evil", "Evil", "pass", true, false, List.of("ROLE_SUPERUSER"));

    assertThrows(
        UserForbiddenException.class, () -> userService.createUser(request, admin));
  }

  @Test
  void adminCannotCreateAdmin() {
    Authentication admin = authWith("admin", "ROLE_ADMIN");
    User request =
        new User(0, "newadmin", "New Admin", "pass", true, false, List.of("ROLE_ADMIN"));

    assertThrows(
        UserForbiddenException.class, () -> userService.createUser(request, admin));
  }

  // --- deleteUser: in-use check ---

  @Test
  void deleteUserWithEventsThrowsInUse() {
    when(eventLogRepository.existsByUserId(1L)).thenReturn(true);

    Authentication superuser = authWith("superuser", "ROLE_SUPERUSER");

    UserInUseException e =
        assertThrows(
            UserInUseException.class, () -> userService.deleteUser(1, superuser));
    assertTrue(e.getMessage().contains("recorded events or comments"));
    verify(userRepository, never()).deleteById(anyLong());
  }

  @Test
  void deleteUserWithCommentsThrowsInUse() {
    when(eventLogRepository.existsByUserId(1L)).thenReturn(false);
    when(quickCommentService.existsByUserId(1L)).thenReturn(true);

    Authentication superuser = authWith("superuser", "ROLE_SUPERUSER");

    UserInUseException e =
        assertThrows(
            UserInUseException.class, () -> userService.deleteUser(1, superuser));
    assertTrue(e.getMessage().contains("recorded events or comments"));
    verify(userRepository, never()).deleteById(anyLong());
  }

  @Test
  void deleteUserWithNoDataSucceeds() {
    when(eventLogRepository.existsByUserId(1L)).thenReturn(false);
    when(quickCommentService.existsByUserId(1L)).thenReturn(false);

    Authentication superuser = authWith("superuser", "ROLE_SUPERUSER");

    userService.deleteUser(1, superuser);

    verify(userRepository).deleteById(1L);
  }

  @Test
  void adminCannotDeleteSuperuser() {
    User existing =
        new User(1, "superuser", "superuser", "hash", true, false, List.of("ROLE_SUPERUSER"));
    when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

    Authentication admin = authWith("admin", "ROLE_ADMIN");

    assertThrows(
        UserForbiddenException.class, () -> userService.deleteUser(1, admin));
    verify(userRepository, never()).deleteById(anyLong());
  }
}
