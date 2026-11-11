package uk.bit1.engagement.application.patient.query;

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
