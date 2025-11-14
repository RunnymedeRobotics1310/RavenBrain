package ca.team1310.ravenbrain.frcapi.service;

/**
 * @author Tony Field
 * @since 2025-11-12 11:50
 */
public class ServiceResponse<T> {
  private final long id;
  private final T response;

  public ServiceResponse(long id, T response) {
    this.id = id;
    this.response = response;
  }

  public long getId() {
    return id;
  }

  public T getResponse() {
    return response;
  }
}
