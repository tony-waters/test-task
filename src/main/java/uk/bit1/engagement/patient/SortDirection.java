package uk.bit1.engagement.patient;

public enum SortDirection {
  ASC,
  DESC;

  public SortDirection reversed() {
    return this == ASC ? DESC : ASC;
  }

  public String param() {
    return name().toLowerCase();
  }
}
