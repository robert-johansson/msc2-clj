# MSC2 Data Model

This document summarizes the main C structs underpinning MSC2. Understanding their layout is essential when mirroring them in the Clojure port.

## Terms & Symbols

- **`Term` (`src/Term.h`)**: fixed-size binary tree (array of `Atom`, default length 64). Each node stores a symbol or operator. Helper functions support hashing (`Term_Hash`), subterm extraction (`Term_ExtractSubterm`), and substitution.
- **`Narsese` parser (`src/Narsese.c`)**: Converts textual input into canonical form (single-char copulas, normalized variables) and builds `Term`s. Also handles procedural operators (`^op`), temporal sequences (`&/`), and predictive implications (`=/>`).

## Truth & Stamps

- **`Truth` (`src/Truth.h`)**: `(float frequency, float confidence)`.
- **`Stamp`**: set of source IDs + timestamps; prevents evidential overlap during revision.

## Events

- **`Event` (`src/Event.h`)**: `Term term`, `Truth truth`, `Stamp stamp`, `bool type (belief/goal)`, `long occurrenceTime`, priority metadata.
- Events flow through:
  - **FIFO (`src/FIFO.c`)**: short-term sequence buffer keyed by channel.
  - **Cycling queues** (`Memory.c` + `PriorityQueue.c`): belief and goal queues ordered by priority and occurrence time.

## Concepts

- **`Concept` (`src/Concept.h`)**: central knowledge unit keyed by a `Term`.
  - `belief` / `goal` spikes: current best belief/goal event associated with the concept.
  - `predict_belief`: cached predicted event for fast lookups.
  - `precondition_beliefs[OPERATIONS_MAX]`: implication tables per operation (see below).
  - `usage` (from `Usage.h`): track `useCount`, `lastUsed`.
  - `priority`: drives attention queues.
  - `term_links`: cached subterms for quick linking.

## Implications & Tables

- **`Implication` (`src/Implication.h`)**: `Term term`, `Truth truth`, `long occurrenceTimeOffset`, metadata flags.
- **`Table` (`src/Table.c`)**: stores a bounded, sorted array of `Implication`s for each concept/operation pair. Supports add, revise-in-place, and selection by expectation.

## Global Structures

- **`Memory` (`src/Memory.c`)**: orchestrates concept storage (hash table + priority queues), adds input events, manages cycling queues, handles forgetting.
- **`PriorityQueue`**: min-max heap implementation with fixed capacity (used for concepts and event queues).
- **`HashTable`**: open-addressed hash for quick concept lookup by term hash.

## Configuration Constants

Set in `src/Config.h`:
- Capacities (`CONCEPT_CAP`, `FIFO_SIZE`, `TABLE_SIZE`, etc.).
 - Temporal parameters (`EVENT_DURABILITY`, `PROJECTION_DECAY`).
 - Decision parameters (`DECISION_THRESHOLD`, `MOTOR_BABBLING_CHANCE`).

These structures form the “object model” replicated in the Clojure port; keep their field semantics aligned to ensure experiments behave identically.***
