package uk.bit1.engagement.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A single thing a Patient recorded at a point in time, within one Topic. */
@Entity
@Table(name = "action")
public class Action {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private UUID uuid;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "patient_id", nullable = false)
  private Patient patient;

  @Column(nullable = false)
  private Instant recordedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Module module;

  @Column(nullable = false)
  private String topic;

  @Column(nullable = false)
  private String actionType;

  @Column(nullable = false)
  private Instant sourceCreatedAt;

  protected Action() {}

  public Long getId() {
    return id;
  }

  public UUID getUuid() {
    return uuid;
  }

  public Patient getPatient() {
    return patient;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public Module getModule() {
    return module;
  }

  public String getTopic() {
    return topic;
  }

  public String getActionType() {
    return actionType;
  }

  public Instant getSourceCreatedAt() {
    return sourceCreatedAt;
  }
}
