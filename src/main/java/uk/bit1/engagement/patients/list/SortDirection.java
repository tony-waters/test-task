package uk.bit1.engagement.patients.list;

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
