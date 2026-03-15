package com.demo.typeclass

import com.demo.models.*

// --- Type class: JsonSerializer[A] ---

trait JsonSerializer[A]:
  extension (a: A) def serialize: String

object JsonSerializer:

  // --- Primitive instances ---

  given JsonSerializer[String] with
    extension (a: String) def serialize: String =
      s""""${a.replace("\\", "\\\\").replace("\"", "\\\"")}""""

  given JsonSerializer[Int] with
    extension (a: Int) def serialize: String = a.toString

  given JsonSerializer[Double] with
    extension (a: Double) def serialize: String = a.toString

  given JsonSerializer[Boolean] with
    extension (a: Boolean) def serialize: String = a.toString

  // --- Recursive instances for containers ---

  given [A](using inner: JsonSerializer[A]): JsonSerializer[List[A]] with
    extension (xs: List[A]) def serialize: String =
      xs.map(_.serialize).mkString("[", ", ", "]")

  given [A](using inner: JsonSerializer[A]): JsonSerializer[Option[A]] with
    extension (opt: Option[A]) def serialize: String =
      opt match
        case Some(a) => a.serialize
        case None    => "null"

  // --- Domain instance for Task ---

  given JsonSerializer[Priority] with
    extension (p: Priority) def serialize: String =
      s""""${p.toString}""""

  given JsonSerializer[TaskStatus] with
    extension (s: TaskStatus) def serialize: String =
      s""""${s.label}""""

  given JsonSerializer[Task] with
    extension (t: Task) def serialize: String =
      val fields = List(
        s""""id": ${t.id}""",
        s""""title": ${t.title.serialize}""",
        s""""description": ${t.description.serialize}""",
        s""""priority": ${t.priority.serialize}""",
        s""""status": ${t.status.serialize}""",
        s""""tags": ${t.tags.serialize}""",
        s""""createdAt": ${t.createdAt.toString.serialize}"""
      )
      fields.mkString("{", ", ", "}")

  // --- Extension method via context bound ---

  extension [A: JsonSerializer](a: A)
    def toJson: String = a.serialize

// --- Utility: serialize any collection to a JSON array ---

def serializeAll[A: JsonSerializer](items: List[A]): String =
  items.map(_.serialize).mkString("[\n  ", ",\n  ", "\n]")

// --- Demo ---

@main def typeClassDemo(): Unit =
  import JsonSerializer.given
  import JsonSerializer.toJson

  println("=" * 60)
  println("  Type Class Demo: JsonSerializer")
  println("=" * 60)

  // Primitives
  println()
  println("--- Primitives ---")
  println(s"  String: ${"hello world".toJson}")
  println(s"  Int:    ${42.toJson}")
  println(s"  Double: ${3.14.toJson}")

  // Containers
  println()
  println("--- Containers ---")
  println(s"  List[Int]:      ${List(1, 2, 3).toJson}")
  println(s"  Option[String]: ${Some("present").toJson}")
  println(s"  None:           ${Option.empty[String].toJson}")
  println(s"  List[Option[Int]]: ${List(Some(1), None, Some(3)).toJson}")

  // Domain model
  println()
  println("--- Task serialization ---")

  val task1 = Task.create(
    "Implement type classes",
    Some("Add JsonSerializer support"),
    Priority.High,
    List("scala", "fp")
  )
  val task2 = Task.create(
    "Review PR",
    None,
    Priority.Medium,
    List("review")
  )

  println(task1.toJson)
  println()
  println(task2.toJson)

  // Batch serialization using context bound function
  println()
  println("--- Batch serialization ---")
  println(serializeAll(List(task1, task2)))

  // Demonstrate context bound in a generic function
  println()
  println("--- Generic function with context bound ---")
  def printAsJson[A: JsonSerializer](label: String, value: A): Unit =
    println(s"  $label => ${value.toJson}")

  printAsJson("priority", Priority.Critical)
  printAsJson("tags", List("scala", "fp", "type-classes"))
  printAsJson("maybe", Option.empty[Int])

  println()
  println("=" * 60)
