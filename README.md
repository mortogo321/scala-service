# Scala Functional Task Management Service

A demonstration project showcasing functional programming concepts in Scala 3 through a task management domain model. The service manages tasks with priorities, statuses, and tags using purely functional patterns -- no mutable state, no side effects in the service layer.

## Tech Stack

- **Scala 3.3.4** (LTS)
- **sbt** build tool
- JDK 11+

## Project Structure

```
src/main/scala/com/demo/
  Main.scala                  -- Entry point and demo runner
  models/
    Models.scala              -- Domain types: Task, TaskStatus, Priority
  service/
    TaskService.scala         -- TaskStore (immutable state) and query pipelines
    ValidationService.scala   -- Either-based error handling with ServiceError ADT
  api/
    Routes.scala              -- Request routing via pattern matching
  typeclass/
    TypeClasses.scala         -- JsonSerializer type class with givens and extensions
  functional/
    State.scala               -- State monad for composable state transitions
```

## Key Concepts Demonstrated

### Enums with Parameters and Ordering

`Priority` is a Scala 3 `enum` where each case carries an integer level. A `given Ordering[Priority]` instance enables sorting tasks by priority without boilerplate comparators.

### Sealed Traits and Exhaustive Pattern Matching

`TaskStatus` is modeled as a sealed trait with case objects (`Todo`, `InProgress`, `Done`, `Cancelled`). The compiler enforces that every match expression covers all cases, as seen in `TaskStatus.describe` and `Router.handle`.

### Immutable State Management with Case Classes

`TaskStore` holds a `Map[Long, Task]` and every mutation method (`add`, `complete`, `cancel`, `startWork`) returns a new `TaskStore` instance. The original is never modified. Terminal states (`Done`, `Cancelled`) are protected -- attempts to transition out of them return `None`.

### For-Comprehensions over Option

State transitions in `Main` are chained using a for-comprehension over `Option[TaskStore]`. If any step fails (e.g., invalid task ID or terminal state), the entire chain short-circuits to `None`.

### Pure Functions with No Side Effects

All methods on `TaskStore` are pure: they take inputs and return new values without modifying any external state. Side effects (printing) are isolated to `Main`.

### Functional Pipelines (filter / map / collect / groupBy / flatMap)

Query methods demonstrate idiomatic Scala collection pipelines:

- **filter/map** -- `highPriorityActiveTasks` filters active tasks by priority, then sorts.
- **collect with partial functions** -- `criticalTaskTitles` and `completedTaskReport` use `collect` with pattern guards and destructuring.
- **flatMap/groupBy** -- `tagCloud` flattens all tags across tasks and groups by identity to count occurrences.
- **groupBy/map** -- `statistics` groups tasks by status and priority to produce aggregate counts.

### Pattern Matching on Request Types

`Router.handle` matches on a `RequestType` enum to dispatch requests, demonstrating nested pattern matching (matching `Option` results inside enum cases) and exhaustive coverage of all request variants.

### Type Classes with Givens and Extension Methods

`JsonSerializer[A]` is a type class trait with a `serialize` extension method. Given instances are provided for primitives (`String`, `Int`, `Double`, `Boolean`), recursive containers (`List[A]`, `Option[A]`), and domain types (`Task`, `Priority`, `TaskStatus`). A `toJson` extension method uses context bounds (`[A: JsonSerializer]`) so any type with a given instance in scope can be serialized with `value.toJson`. The generic `serializeAll` function demonstrates how context bounds propagate through call chains.

Run with: `sbt "runMain com.demo.typeclass.typeClassDemo"`

### Either-based Error Handling

`ServiceError` is a sealed trait ADT with cases `NotFound`, `ValidationError`, and `Conflict`. `ValidatedTaskService` wraps every operation in `Either[ServiceError, T]`, enabling for-comprehension chaining where any validation failure short-circuits the pipeline. The demo shows recovery patterns: `.fold` for dual-branch transformation, `.getOrElse` for defaults, `.orElse` for fallback lookups, `.left.map` for error-side transformation, and pattern matching for granular error dispatch.

Run with: `sbt "runMain com.demo.service.validationDemo"`

### State Monad for Composable State Transitions

A minimal `State[S, A]` monad wraps `S => (S, A)` with `map` and `flatMap` for for-comprehension support. The companion object provides `pure`, `get`, `set`, `modify`, and `inspect`. `TaskStoreActions` re-expresses `TaskStore` operations (`addTask`, `startWork`, `completeTask`, etc.) as `State[TaskStore, A]` values that compose declaratively. Programs are built as pure descriptions and only executed when `.run(initialState)` is called, cleanly separating program construction from execution.

Run with: `sbt "runMain com.demo.functional.stateMonadDemo"`

## How to Run

```bash
sbt run
```

## Sample Output

```
============================================================
  Scala Functional Task Management Service Demo
============================================================

--- Creating Tasks ---

[Critical] Implement auth service (JWT-based authentication) - To Do [tags: backend, security]
[Critical] Fix N+1 query bug (Optimize user listing endpoint) - To Do [tags: backend, database, bugfix]
[High] Design database schema (Define tables for user and task entities) - To Do [tags: backend, database]
[High] Add caching layer (Redis integration for hot data) - To Do [tags: backend, performance]
[Medium] Set up CI/CD pipeline (GitHub Actions for build and deploy) - To Do [tags: devops, automation]
[Medium] Frontend login page (React component for login) - To Do [tags: frontend]
[Low] Write API documentation (No description) - To Do [tags: docs]

--- Updating Task Statuses ---

[Critical] Implement auth service (JWT-based authentication) - In Progress [tags: backend, security]
[High] Add caching layer (Redis integration for hot data) - To Do [tags: backend, performance]
[Medium] Set up CI/CD pipeline (GitHub Actions for build and deploy) - To Do [tags: devops, automation]
[Medium] Frontend login page (React component for login) - To Do [tags: frontend]
[High] Design database schema (Define tables for user and task entities) - Done [tags: backend, database]
[Critical] Fix N+1 query bug (Optimize user listing endpoint) - Done [tags: backend, database, bugfix]
[Low] Write API documentation (No description) - Cancelled [tags: docs]

--- Terminal State Protection ---

Cannot transition task #1 from Done - terminal state protected
Cannot start task #4 from Cancelled - terminal state protected

--- Query: High Priority Active Tasks ---

  [Critical] Implement auth service (JWT-based authentication) - In Progress [tags: backend, security]
  [High] Add caching layer (Redis integration for hot data) - To Do [tags: backend, performance]

--- Statistics ---

Task Statistics
  Total: 7 | Active: 4 | Completed: 2
  ...

--- Priority Ordering ---

  Sorted (desc): Critical > High > Medium > Low

--- Router: Pattern Matching on Requests ---

  Request: GetById(3)
  [OK] (200)
  [Critical] Implement auth service (JWT-based authentication) - In Progress [tags: backend, security]

  Request: GetById(99)
  [ERR] (404)
  Task #99 not found

  Request: GetStats
  [OK] (200)
  Task Statistics
  ...

============================================================
  Demo complete.
============================================================
```

## License

This project is provided for educational and demonstration purposes.
