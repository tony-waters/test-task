package uk.bit1.engagement.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A person invited onto the pathway. Rows are written only by the loader; the app reads them. */
@Entity
@Table(name = "patient")
public class Patient {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private UUID uuid;

  private String title;

  @Column(nullable = false)
  private String givenName;

  @Column(nullable = false)
  private String familyName;

  private String gender;

  private LocalDate dateOfBirth;

  private String nhsNumber;

  private String hospitalId;

  @Column(nullable = false)
  private Instant invitedAt;

  private Instant registeredAt;

  private Instant dischargedAt;

  @Column(nullable = false)
  private Instant sourceCreatedAt;

  /** A generated column in the database: read-only here. */
  @Enumerated(EnumType.STRING)
  @Column(insertable = false, updatable = false)
  private Status status;

  protected Patient() {}

  public Long getId() {
    return id;
  }

  public UUID getUuid() {
    return uuid;
  }

  public String getTitle() {
    return title;
  }

  public String getGivenName() {
    return givenName;
  }

  public String getFamilyName() {
    return familyName;
  }

  public String getGender() {
    return gender;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public String getNhsNumber() {
    return nhsNumber;
  }

  public String getHospitalId() {
    return hospitalId;
  }

  public Instant getInvitedAt() {
    return invitedAt;
  }

  public Instant getRegisteredAt() {
    return registeredAt;
  }

  public Instant getDischargedAt() {
    return dischargedAt;
  }

  public Instant getSourceCreatedAt() {
    return sourceCreatedAt;
  }

  public Status getStatus() {
    return status;
  }
}
