# Timestamps are UTC, shown and grouped in Europe/London

The source data's timestamps have no time zone. We decided they are UTC instants: they are stored as `timestamptz`, and every display, date-range filter, weekly grouping and age calculation uses Europe/London (ISO weeks, Monday start). UTC is the conventional neutral meaning for unlabelled machine timestamps. The users are UK clinical staff, so "which week" and "which day" must follow their calendar, including the BST shift.

## Consequences

- If the source turns out to have been London local time, every stored instant is off by 0 or 1 hour, depending on the season. Fixing it means re-importing, not just changing the display.
- Actions recorded close to midnight UTC during BST belong to the *next* London day. This can move them across a week boundary.
