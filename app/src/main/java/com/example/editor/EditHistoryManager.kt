package com.example.editor

data class HistoryEntry<T>(
  val state: T,
  val actionLabel: String,
)

data class HistoryTransition<T>(
  val newState: T,
  val label: String,
)

/**
 * Deterministic Undo/Redo history stack supporting both discrete edit actions
 * (filters, aspect presets, rotations, flips) and continuous drag gestures
 * (crop handles, intensity/adjustment/straighten sliders).
 */
class EditHistoryManager<T>(
  initialState: T,
  private val maxSteps: Int = 30,
) {
  var current: T = initialState
    private set

  private val undoStack = ArrayDeque<HistoryEntry<T>>()
  private val redoStack = ArrayDeque<HistoryEntry<T>>()
  private var pendingGestureStart: T? = null

  val canUndo: Boolean get() = undoStack.isNotEmpty()
  val canRedo: Boolean get() = redoStack.isNotEmpty()
  val undoCount: Int get() = undoStack.size
  val redoCount: Int get() = redoStack.size
  val nextUndoLabel: String? get() = undoStack.lastOrNull()?.actionLabel
  val nextRedoLabel: String? get() = redoStack.lastOrNull()?.actionLabel

  fun reset(initialState: T) {
    current = initialState
    undoStack.clear()
    redoStack.clear()
    pendingGestureStart = null
  }

  fun commit(newState: T, actionLabel: String): Boolean {
    if (newState == current) {
      pendingGestureStart = null
      return false
    }
    undoStack.addLast(HistoryEntry(current, actionLabel))
    while (undoStack.size > maxSteps) {
      undoStack.removeFirst()
    }
    redoStack.clear()
    current = newState
    pendingGestureStart = null
    return true
  }

  fun beginGesture(startState: T = current) {
    if (pendingGestureStart == null) {
      pendingGestureStart = startState
    }
  }

  fun updateGesturePreview(previewState: T) {
    current = previewState
  }

  fun finishGesture(actionLabel: String): Boolean {
    val start = pendingGestureStart ?: return false
    pendingGestureStart = null
    if (current == start) return false
    undoStack.addLast(HistoryEntry(start, actionLabel))
    while (undoStack.size > maxSteps) {
      undoStack.removeFirst()
    }
    redoStack.clear()
    return true
  }

  fun undo(): HistoryTransition<T>? {
    val entry = undoStack.removeLastOrNull() ?: return null
    redoStack.addLast(HistoryEntry(current, entry.actionLabel))
    while (redoStack.size > maxSteps) {
      redoStack.removeFirst()
    }
    current = entry.state
    pendingGestureStart = null
    return HistoryTransition(newState = entry.state, label = entry.actionLabel)
  }

  fun redo(): HistoryTransition<T>? {
    val entry = redoStack.removeLastOrNull() ?: return null
    undoStack.addLast(HistoryEntry(current, entry.actionLabel))
    while (undoStack.size > maxSteps) {
      undoStack.removeFirst()
    }
    current = entry.state
    pendingGestureStart = null
    return HistoryTransition(newState = entry.state, label = entry.actionLabel)
  }
}
