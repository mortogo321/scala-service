package com.demo.api

import com.demo.models.*
import com.demo.service.*

// --- Simulated request/response types ---

enum RequestType:
  case GetAll, GetById(id: Long), GetByPriority(priority: Priority)
  case GetByTag(tag: String), GetStats, Search(query: String)

case class Response(status: Int, body: String)

// --- Router: pattern matching on request types ---

object Router:

  def handle(request: RequestType, store: TaskStore): Response =
    request match
      case RequestType.GetAll =>
        val summaries = store.taskSummaries
        if summaries.isEmpty then Response(200, "No tasks found.")
        else Response(200, summaries.mkString("\n"))

      case RequestType.GetById(id) =>
        store.findById(id) match
          case Some(task) => Response(200, task.summary)
          case None       => Response(404, s"Task #$id not found")

      case RequestType.GetByPriority(priority) =>
        val tasks = store.findByPriority(priority)
        if tasks.isEmpty then Response(200, s"No $priority priority tasks.")
        else Response(200, tasks.map(_.summary).mkString("\n"))

      case RequestType.GetByTag(tag) =>
        val tasks = store.findByTag(tag)
        if tasks.isEmpty then Response(200, s"No tasks tagged '$tag'.")
        else Response(200, tasks.map(_.summary).mkString("\n"))

      case RequestType.GetStats =>
        Response(200, store.statistics.formatted)

      case RequestType.Search(query) =>
        val results = store.searchByTitle(query)
        if results.isEmpty then Response(200, s"No tasks matching '$query'.")
        else Response(200, results.map(_.summary).mkString("\n"))

  // --- Batch processing with functional composition ---

  def handleBatch(requests: List[RequestType], store: TaskStore): List[Response] =
    requests.map(handle(_, store))

  def formatResponse(response: Response): String =
    val statusEmoji = if response.status == 200 then "[OK]" else "[ERR]"
    s"$statusEmoji (${response.status})\n${response.body}"
