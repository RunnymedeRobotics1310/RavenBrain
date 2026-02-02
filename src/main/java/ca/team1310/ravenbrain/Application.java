package ca.team1310.ravenbrain;

import io.micronaut.runtime.Micronaut;

public class Application {

  static void main(String[] args) {
    Micronaut.run(Application.class, args);
  }

  public static String getVersion() {
    String version = Application.class.getPackage().getImplementationVersion();
    return version != null ? version : "Development Build";
  }
}
