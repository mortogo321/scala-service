package com.demo.functional

import com.demo.models.*
import com.demo.service.TaskStore

// --- State monad ---

case class State[S, A](run: S => (S, A)):

  def map[B](f: A => B): State[S, B] =
    State: s =>
      val (s1, a) = run(s)
      (s1, f(a))

  def flatMap[B](f: A => State[S, B]): State[S, B] =
    State: s =>
      val (s1, a) = run(s)
      f(a).run(s1)

object State:

  def pure[S, A](a: A): State[S, A] =
    State(s => (s, a))

  def get[S]: State[S, S] =
    State(s => (s, s))

  def set[S](s: S): State[S, Unit] =
    State(_ => (s, ()))

  def modify[S](f: S => S): State[S, Unit] =
    State(s => (f(s), ()))

  def inspect[S, A](f: S => A): State[S, A] =
    State(s => (s, f(s)))

// --- TaskStore operations as State actions ---

object TaskStoreActions:

  type TaskState[A] = State[TaskStore, A]

  def addTask(task: Task): TaskState[Task] =
    State: store =>
      (store.add(task), task)

  def getTask(id: Long): TaskState[Option[Task]] =
    State.inspect(_.findById(id))

  def startWork(id: Long): TaskState[Option[Task]] =
    State: store =>
      store.startWork(id) match
        case Some(updated) => (updated, updated.findById(id))
        case None          => (store, None)

  def completeTask(id: Long): TaskState[Option[Task]] =
    State: store =>
      store.complete(id) match
        case Some(updated) => (updated, updated.findById(id))
        case None          => (store, None)

  def cancelTask(id: Long): TaskState[Option[Task]] =
    State: store =>
      store.cancel(id) match
        case Some(updated) => (updated, updated.findById(id))
        case None          => (store, None)

  def addTags(id: Long, tags: List[String]): TaskState[Option[Task]] =
    State: store =>
      store.addTags(id, tags) match
        case Some(updated) => (updated, updated.findById(id))
        case None          => (store, None)

  def activeTasks: TaskState[List[Task]] =
    State.inspect(_.activeTasks)

  def taskCount: TaskState[Int] =
    State.inspect(_.tasks.size)

  def highPriorityActive: TaskState[List[Task]] =
    State.inspect(_.highPriorityActiveTasks)

// --- Demo ---

@main def stateMonadDemo(): Unit =
  import TaskStoreActions.*

  println("=" * 60)
  println("  State Monad Demo: Composable State Transitions")
  println("=" * 60)

  // --- Build a composed state program using for-comprehension ---
  val program: TaskState[String] = for
    // Create tasks
    t1 <- addTask(Task.create("Design schema", Some("ER diagram"), Priority.High, List("db")))
    t2 <- addTask(Task.create("Implement API", Some("REST endpoints"), Priority.Critical, List("backend")))
    t3 <- addTask(Task.create("Write docs", None, Priority.Low, List("docs")))

    // Query intermediate state
    count1  <- taskCount
    active1 <- activeTasks

    // Transition states
    _ <- startWork(t1.id)
    _ <- startWork(t2.id)
    _ <- completeTask(t1.id)
    _ <- cancelTask(t3.id)

    // Tag a task
    _ <- addTags(t2.id, List("api", "priority"))

    // Query final state
    count2    <- taskCount
    active2   <- activeTasks
    highPri   <- highPriorityActive
    remaining <- getTask(t2.id)
  yield
    val lines = List(
      s"After creation:   $count1 tasks, ${active1.size} active",
      s"After transitions: $count2 tasks, ${active2.size} active",
      s"High priority active: ${highPri.map(_.title).mkString(", ")}",
      s"Task #${t2.id} status: ${remaining.map(_.status.label).getOrElse("unknown")}",
      s"Task #${t2.id} tags: ${remaining.map(_.tags.mkString(", ")).getOrElse("none")}"
    )
    lines.mkString("\n  ")

  // --- Execute the state program ---
  val initialStore = TaskStore()
  val (finalStore, report) = program.run(initialStore)

  println()
  println("--- Program Output ---")
  println(s"  $report")

  // --- The final store is available for further inspection ---
  println()
  println("--- Final Store Statistics ---")
  println(finalStore.statistics.formatted)

  // --- Demonstrate composability: chain two independent programs ---
  println()
  println("--- Composing Independent Programs ---")

  val addMore: TaskState[List[Task]] = for
    a <- addTask(Task.create("Monitoring", Some("Grafana dashboards"), Priority.Medium, List("ops")))
    b <- addTask(Task.create("Load testing", Some("k6 scripts"), Priority.High, List("ops", "perf")))
  yield List(a, b)

  val queryAfter: TaskState[String] = for
    _      <- addMore
    active <- activeTasks
    count  <- taskCount
  yield s"$count total tasks, ${active.size} active"

  val (store2, summary) = queryAfter.run(finalStore)
  println(s"  $summary")

  // --- Demonstrate State.modify and State.inspect ---
  println()
  println("--- Low-level State Operations ---")

  val lowLevel = for
    _     <- State.modify[TaskStore](s => s.add(Task.create("Ad-hoc task", None, Priority.Low, Nil)))
    count <- State.inspect[TaskStore, Int](_.tasks.size)
    store <- State.get[TaskStore]
  yield s"Store has $count tasks, tag cloud: ${store.tagCloud}"

  val (_, lowLevelResult) = lowLevel.run(store2)
  println(s"  $lowLevelResult")

  println()
  println("=" * 60)
