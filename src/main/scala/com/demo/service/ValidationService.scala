package com.demo.service

import com.demo.models.*

// --- Error ADT ---

sealed trait ServiceError:
  def message: String

object ServiceError:
  case class NotFound(id: String) extends ServiceError:
    def message: String = s"Resource not found: $id"

  case class ValidationError(field: String, msg: String) extends ServiceError:
    def message: String = s"Validation failed on '$field': $msg"

  case class Conflict(msg: String) extends ServiceError:
    def message: String = s"Conflict: $msg"

// --- Type alias for clarity ---

type Result[A] = Either[ServiceError, A]

// --- Validated service wrapping TaskStore operations ---

class ValidatedTaskService(private var store: TaskStore):

  // --- Validation helpers ---

  private def validateNonEmpty(field: String, value: String): Result[String] =
    if value.trim.isEmpty then Left(ServiceError.ValidationError(field, "must not be empty"))
    else Right(value.trim)

  private def validatePositive(field: String, value: Long): Result[Long] =
    if value <= 0 then Left(ServiceError.ValidationError(field, "must be positive"))
    else Right(value)

  private def requireTask(id: Long): Result[Task] =
    store.findById(id).toRight(ServiceError.NotFound(s"task#$id"))

  // --- Operations returning Either[ServiceError, T] ---

  def createTask(
      title: String,
      description: Option[String],
      priority: Priority,
      tags: List[String]
  ): Result[Task] =
    for
      validTitle <- validateNonEmpty("title", title)
      validDesc  <- description match
        case Some(d) => validateNonEmpty("description", d).map(Some(_))
        case None    => Right(None)
      validTags  <- {
        val invalid = tags.filter(_.trim.isEmpty)
        if invalid.nonEmpty then Left(ServiceError.ValidationError("tags", "tags must not be blank"))
        else Right(tags.map(_.trim))
      }
    yield
      val task = Task.create(validTitle, validDesc, priority, validTags)
      store = store.add(task)
      task

  def getTask(id: Long): Result[Task] =
    for
      validId <- validatePositive("id", id)
      task    <- requireTask(validId)
    yield task

  def startWork(id: Long): Result[Task] =
    for
      _       <- validatePositive("id", id)
      task    <- requireTask(id)
      _       <- Either.cond(
                   !task.status.isTerminal,
                   (),
                   ServiceError.Conflict(s"task#$id is in terminal state '${task.status.label}'")
                 )
      updated <- store.startWork(id).toRight(
                   ServiceError.Conflict(s"Cannot start task#$id")
                 )
    yield
      store = updated
      updated.findById(id).get

  def completeTask(id: Long): Result[Task] =
    for
      _       <- validatePositive("id", id)
      task    <- requireTask(id)
      _       <- Either.cond(
                   task.status == TaskStatus.InProgress,
                   (),
                   ServiceError.Conflict(
                     s"task#$id must be InProgress to complete, currently '${task.status.label}'"
                   )
                 )
      updated <- store.complete(id).toRight(
                   ServiceError.Conflict(s"Cannot complete task#$id")
                 )
    yield
      store = updated
      updated.findById(id).get

  def addTags(id: Long, newTags: List[String]): Result[Task] =
    for
      _       <- validatePositive("id", id)
      _       <- requireTask(id)
      _       <- {
        val duplicates = newTags.intersect(store.findById(id).get.tags)
        if duplicates.nonEmpty then
          Left(ServiceError.Conflict(s"Tags already present: ${duplicates.mkString(", ")}"))
        else Right(())
      }
      updated <- store.addTags(id, newTags).toRight(
                   ServiceError.NotFound(s"task#$id")
                 )
    yield
      store = updated
      updated.findById(id).get

  def currentStore: TaskStore = store

// --- Demo ---

@main def validationDemo(): Unit =
  println("=" * 60)
  println("  Either-based Error Handling Demo")
  println("=" * 60)

  val service = ValidatedTaskService(TaskStore())

  // --- Happy path ---
  println()
  println("--- Happy Path ---")

  val happyResult = for
    task1 <- service.createTask("Build API", Some("REST endpoints"), Priority.High, List("backend"))
    task2 <- service.createTask("Write tests", Some("Unit + integration"), Priority.Medium, List("testing"))
    _     <- service.startWork(task1.id)
    done  <- service.completeTask(task1.id)
  yield (done, task2)

  happyResult match
    case Right((completed, pending)) =>
      println(s"  Completed: ${completed.summary}")
      println(s"  Pending:   ${pending.summary}")
    case Left(err) =>
      println(s"  Unexpected error: ${err.message}")

  // --- Validation errors ---
  println()
  println("--- Validation Errors ---")

  val emptyTitle = service.createTask("", None, Priority.Low, Nil)
  println(s"  Empty title:    ${emptyTitle.fold(_.message, _.summary)}")

  val badId = service.getTask(-1)
  println(s"  Negative ID:    ${badId.fold(_.message, _.summary)}")

  val notFound = service.getTask(999)
  println(s"  Missing task:   ${notFound.fold(_.message, _.summary)}")

  // --- Conflict errors ---
  println()
  println("--- Conflict Errors ---")

  // Task 1 is already Done; try to start it again
  val restartDone = service.startWork(1)
  println(s"  Restart done:   ${restartDone.fold(_.message, _.summary)}")

  // Task 2 is Todo; try to complete without starting
  val skipStart = service.completeTask(2)
  println(s"  Skip start:     ${skipStart.fold(_.message, _.summary)}")

  // --- Recovery patterns ---
  println()
  println("--- Recovery Patterns ---")

  // .getOrElse: fall back to a default
  val fallback = service.getTask(999).getOrElse(
    Task.create("Fallback task", None, Priority.Low, Nil)
  )
  println(s"  getOrElse:      ${fallback.summary}")

  // .fold: transform both sides
  val description = service.getTask(2).fold(
    err  => s"Error occurred: ${err.message}",
    task => s"Found: ${task.title} [${task.status.label}]"
  )
  println(s"  fold:           $description")

  // pattern match for granular error handling
  val granular = service.startWork(1) match
    case Right(task) => s"Started: ${task.title}"
    case Left(ServiceError.NotFound(id))           => s"Could not find $id"
    case Left(ServiceError.Conflict(msg))          => s"Conflict - $msg"
    case Left(ServiceError.ValidationError(f, m))  => s"Invalid $f: $m"
  println(s"  match:          $granular")

  // .left.map: transform the error side
  val mappedError: Either[String, Task] =
    service.getTask(999).left.map(_.message)
  println(s"  left.map:       $mappedError")

  // Chain with .orElse: try alternative
  val withFallbackLookup = service.getTask(999).orElse(service.getTask(2))
  println(s"  orElse:         ${withFallbackLookup.fold(_.message, _.summary)}")

  println()
  println("=" * 60)
