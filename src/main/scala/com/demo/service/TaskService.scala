package com.demo.service

import com.demo.models.*
import java.time.LocalDateTime

// --- Immutable state container ---

case class TaskStore(tasks: Map[Long, Task] = Map.empty):

  // --- Pure functions returning new TaskStore ---

  def add(task: Task): TaskStore =
    copy(tasks = tasks + (task.id -> task))

  def addAll(newTasks: List[Task]): TaskStore =
    val entries = newTasks.map(t => t.id -> t)
    copy(tasks = tasks ++ entries)

  def updateStatus(id: Long, status: TaskStatus): Option[TaskStore] =
    for
      task <- tasks.get(id)
      if !task.status.isTerminal // can't transition from terminal states
    yield copy(tasks = tasks + (id -> task.withStatus(status)))

  def complete(id: Long): Option[TaskStore] =
    updateStatus(id, TaskStatus.Done)

  def cancel(id: Long): Option[TaskStore] =
    updateStatus(id, TaskStatus.Cancelled)

  def startWork(id: Long): Option[TaskStore] =
    updateStatus(id, TaskStatus.InProgress)

  def addTags(id: Long, newTags: List[String]): Option[TaskStore] =
    tasks.get(id).map: task =>
      copy(tasks = tasks + (id -> task.withTags(newTags)))

  // --- Query methods using functional pipelines ---

  def findById(id: Long): Option[Task] = tasks.get(id)

  def findByPriority(priority: Priority): List[Task] =
    tasks.values.filter(_.priority == priority).toList.sortBy(_.id)

  def findByStatus(status: TaskStatus): List[Task] =
    tasks.values.filter(_.status == status).toList.sortBy(_.id)

  def findByTag(tag: String): List[Task] =
    tasks.values
      .filter(_.tags.exists(_.equalsIgnoreCase(tag)))
      .toList
      .sortBy(_.id)

  def activeTasks: List[Task] =
    tasks.values.filter(_.isActive).toList.sortBy(_.id)

  def searchByTitle(query: String): List[Task] =
    tasks.values
      .filter(_.title.toLowerCase.contains(query.toLowerCase))
      .toList
      .sortBy(_.id)

  // --- Pipeline: chained filter/map/collect ---

  def highPriorityActiveTasks: List[Task] =
    tasks.values.toList
      .filter(_.isActive)
      .filter(t => t.priority == Priority.High || t.priority == Priority.Critical)
      .sortBy(_.priority)(using Ordering[Priority].reverse)

  def tagCloud: Map[String, Int] =
    tasks.values.toList
      .flatMap(_.tags)
      .groupBy(identity)
      .map((tag, occurrences) => tag -> occurrences.size)

  def taskSummaries: List[String] =
    tasks.values.toList
      .sortBy(t => (t.status.isTerminal, -t.priority.level))
      .map(_.summary)

  // --- Statistics using collect and groupBy ---

  def statistics: TaskStatistics =
    val byStatus = tasks.values.toList
      .groupBy(_.status)
      .map((status, group) => status.label -> group.size)

    val byPriority = tasks.values.toList
      .groupBy(_.priority)
      .map((priority, group) => priority.toString -> group.size)

    val activeCount = tasks.values.count(_.isActive)
    val completedCount = tasks.values.count(_.status == TaskStatus.Done)

    TaskStatistics(
      total = tasks.size,
      active = activeCount,
      completed = completedCount,
      byStatus = byStatus,
      byPriority = byPriority
    )

  // --- Collect pattern: extract specific info with partial functions ---

  def criticalTaskTitles: List[String] =
    tasks.values.toList.collect:
      case task if task.priority == Priority.Critical && task.isActive =>
        s"[!] ${task.title}"

  def completedTaskReport: List[String] =
    tasks.values.toList.collect:
      case Task(id, title, _, priority, TaskStatus.Done, tags, _) =>
        val tagStr = if tags.isEmpty then "" else s" (${tags.mkString(", ")})"
        s"  #$id [$priority] $title$tagStr"

// --- Statistics result ---

case class TaskStatistics(
    total: Int,
    active: Int,
    completed: Int,
    byStatus: Map[String, Int],
    byPriority: Map[String, Int]
):
  def formatted: String =
    val statusLines = byStatus.toList.sortBy(_._1).map((k, v) => s"    $k: $v")
    val priorityLines = byPriority.toList.sortBy(_._1).map((k, v) => s"    $k: $v")
    s"""Task Statistics
       |  Total: $total | Active: $active | Completed: $completed
       |  By Status:
       |${statusLines.mkString("\n")}
       |  By Priority:
       |${priorityLines.mkString("\n")}""".stripMargin
