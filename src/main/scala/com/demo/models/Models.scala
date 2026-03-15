package com.demo.models

import java.time.LocalDateTime

// --- Priority: Scala 3 enum with ordering ---

enum Priority(val level: Int):
  case Low      extends Priority(1)
  case Medium   extends Priority(2)
  case High     extends Priority(3)
  case Critical extends Priority(4)

object Priority:
  given Ordering[Priority] = Ordering.by(_.level)

  def fromString(s: String): Option[Priority] =
    Priority.values.find(_.toString.equalsIgnoreCase(s))

// --- TaskStatus: sealed trait + case objects for exhaustive matching ---

sealed trait TaskStatus(val label: String, val isTerminal: Boolean)

object TaskStatus:
  case object Todo       extends TaskStatus("To Do", isTerminal = false)
  case object InProgress extends TaskStatus("In Progress", isTerminal = false)
  case object Done       extends TaskStatus("Done", isTerminal = true)
  case object Cancelled  extends TaskStatus("Cancelled", isTerminal = true)

  val all: List[TaskStatus] = List(Todo, InProgress, Done, Cancelled)

  def describe(status: TaskStatus): String = status match
    case Todo       => "Waiting to be started"
    case InProgress => "Currently being worked on"
    case Done       => "Successfully completed"
    case Cancelled  => "No longer needed"

// --- Task: immutable case class ---

case class Task(
    id: Long,
    title: String,
    description: Option[String],
    priority: Priority,
    status: TaskStatus,
    tags: List[String],
    createdAt: LocalDateTime
):
  def isActive: Boolean = !status.isTerminal

  def withStatus(newStatus: TaskStatus): Task = copy(status = newStatus)

  def withTags(newTags: List[String]): Task = copy(tags = (tags ++ newTags).distinct)

  def summary: String =
    val desc = description.getOrElse("No description")
    val tagStr = if tags.isEmpty then "none" else tags.mkString(", ")
    s"[$priority] $title ($desc) - ${status.label} [tags: $tagStr]"

object Task:
  private var counter: Long = 0

  def create(
      title: String,
      description: Option[String] = None,
      priority: Priority = Priority.Medium,
      tags: List[String] = Nil
  ): Task =
    counter += 1
    Task(
      id = counter,
      title = title,
      description = description,
      priority = priority,
      status = TaskStatus.Todo,
      tags = tags,
      createdAt = LocalDateTime.now()
    )
