package uk.bit1.engagement.patient;

import java.util.List;

/** One page of the patient list, with the sort that produced it. */
public record PatientPage(
    List<PatientSummary> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    String sort,
    String direction) {}
