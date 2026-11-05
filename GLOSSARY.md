# Patient Engagement

A view over Patients invited onto a digital care pathway and the Actions they record in it, for clinical and administrative staff.

## Patients and their journey

**Patient**:
A person invited onto the pathway, identified by an NHS number and/or a hospital ID.
_Avoid_: User, member, account

**Invitation**:
The moment a Patient was asked to join. Every Patient has one.
_Avoid_: Referral, enrolment

**Registration**:
The moment a Patient accepted the Invitation and joined. Some Patients never register.
_Avoid_: Sign-up, activation, onboarding

**Discharge**:
The moment a Patient was formally released from the pathway. It ends their Status but not their ability to record Actions.
_Avoid_: Completion, exit, closure

**Status**:
A Patient's current stage, which is the latest of Invited, Registered and Discharged that they have reached. It is always derived from the journey timestamps, never set directly.
_Avoid_: State, stage (when meaning the current one)

**Skipped registration**:
A Patient who was discharged without ever registering. Their Status is Discharged, and they are flagged as a data-quality concern.

## Actions

**Action**:
A single thing a Patient recorded at a point in time, within one Topic.
_Avoid_: Activity, event, interaction

**Action type**:
The kind of Action: started, task completed, task skipped, finished, abandoned, or completed (for assessments).
_Avoid_: Activity, verb, event type

**Module**:
One of the three broad kinds of pathway content: Assessment, Diary or Programme.
_Avoid_: Category, area, context

**Topic**:
A specific named piece of content within exactly one Module, such as the Fatigue programme, the Bladder diary or the PHQ9 assessment.
_Avoid_: Context, activity, instrument

**Post-discharge Action**:
An Action recorded after the Patient's Discharge. It is legitimate, and it is counted like any other.

**Suspect timestamp**:
An Action whose recorded time is at or after the moment the data was extracted. It is kept, and it is flagged as a data-quality concern.

## Measures

**Active patient**:
A Patient who recorded at least one Action in a given week (Monday to Sunday, UK time).
_Avoid_: Engaged patient, live patient

**Engagement summary**:
A Patient's counts of Actions by Action type, for each Module and Topic, with the first and last time they recorded one.
