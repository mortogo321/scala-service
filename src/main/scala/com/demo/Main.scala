package com.demo

import com.demo.models.*
import com.demo.service.*
import com.demo.api.*

@main def run(): Unit =
  println("=" * 60)
  println("  Scala Functional Task Management Service Demo")
  println("=" * 60)

  // --- 1. Create tasks (immutable state transitions) ---
  section("Creating Tasks")

  val store = TaskStore()
    .add(Task.create("Design database schema", Some("Define tables for user and task entities"), Priority.High, List("backend", "database")))
    .add(Task.create("Set up CI/CD pipeline", Some("GitHub Actions for build and deploy"), Priority.Medium, List("devops", "automation")))
    .add(Task.create("Implement auth service", Some("JWT-based authentication"), Priority.Critical, List("backend", "security")))
    .add(Task.create("Write API documentation", None, Priority.Low, List("docs")))
    .add(Task.create("Fix N+1 query bug", Some("Optimize user listing endpoint"), Priority.Critical, List("backend", "database", "bugfix")))
    .add(Task.create("Add caching layer", Some("Redis integration for hot data"), Priority.High, List("backend", "performance")))
    .add(Task.create("Frontend login page", Some("React component for login"), Priority.Medium, List("frontend")))

  store.taskSummaries.foreach(println)

  // --- 2. Update tasks (pure state transitions) ---
  section("Updating Task Statuses")

  val updated = (for
    s1 <- store.startWork(1)     // Design DB schema -> InProgress
    s2 <- s1.complete(1)         // Design DB schema -> Done
    s3 <- s2.startWork(3)        // Auth service -> InProgress
    s4 <- s3.startWork(5)        // Fix N+1 bug -> InProgress
    s5 <- s4.complete(5)         // Fix N+1 bug -> Done
    s6 <- s5.cancel(4)           // Write API docs -> Cancelled
  yield s6).getOrElse(store)

  updated.taskSummaries.foreach(println)

  // --- 3. Demonstrate terminal state immutability ---
  section("Terminal State Protection")

  val reopenAttempt = updated.updateStatus(1, TaskStatus.InProgress) // task #1 is Done
  reopenAttempt match
    case Some(_) => println("Reopened task (unexpected)")
    case None    => println("Cannot transition task #1 from Done - terminal state protected")

  val cancelAttempt = updated.startWork(4) // task #4 is Cancelled
  cancelAttempt match
    case Some(_) => println("Started cancelled task (unexpected)")
    case None    => println("Cannot start task #4 from Cancelled - terminal state protected")

  // --- 4. Query with functional pipelines ---
  section("Query: High Priority Active Tasks")
  updated.highPriorityActiveTasks.foreach(t => println(s"  ${t.summary}"))

  section("Query: Tasks by Tag 'backend'")
  updated.findByTag("backend").foreach(t => println(s"  ${t.summary}"))

  section("Query: Critical Task Alerts")
  updated.criticalTaskTitles.foreach(println)

  section("Query: Completed Task Report")
  updated.completedTaskReport.foreach(println)

  // --- 5. Statistics ---
  section("Statistics")
  println(updated.statistics.formatted)

  // --- 6. Tag cloud ---
  section("Tag Cloud")
  updated.tagCloud.toList
    .sortBy(-_._2)
    .foreach((tag, count) => println(s"  $tag: $count"))

  // --- 7. Pattern matching on status descriptions ---
  section("Status Descriptions (Exhaustive Pattern Match)")
  TaskStatus.all.foreach: status =>
    println(s"  ${status.label}: ${TaskStatus.describe(status)}")

  // --- 8. Priority enum features ---
  section("Priority Ordering")
  val sorted = List(Priority.Low, Priority.Critical, Priority.Medium, Priority.High)
    .sorted(using Ordering[Priority].reverse)
  println(s"  Sorted (desc): ${sorted.mkString(" > ")}")

  // --- 9. Router demo ---
  section("Router: Pattern Matching on Requests")

  val requests = List(
    RequestType.GetById(3),
    RequestType.GetById(99),
    RequestType.GetByPriority(Priority.Critical),
    RequestType.GetByTag("devops"),
    RequestType.GetStats
  )

  requests.foreach: req =>
    val response = Router.handle(req, updated)
    println(s"  Request: $req")
    println(s"  ${Router.formatResponse(response)}")
    println()

  println("=" * 60)
  println("  Demo complete.")
  println("=" * 60)

// --- Helper ---

def section(title: String): Unit =
  println()
  println(s"--- $title ---")
  println()
