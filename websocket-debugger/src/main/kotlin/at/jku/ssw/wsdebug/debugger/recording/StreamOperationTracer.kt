package at.jku.ssw.wsdebug.debugger.recording

class StreamOperationTracer {
    var streamtrace = mutableListOf<StreamOperationValue>()
    val lastTraceValue: StreamOperationValue?
        get() = streamtrace.lastOrNull()
    var lastInOps = mutableMapOf<Int, StreamOperationValue>()
    var sortedTrace = mutableListOf<StreamOperationValue>()

    var sequenceCounter = 1
    var elementcounter = 1

    var visualizationObjects = StreamVisualizationObjects(
        marbles = mutableListOf(),
        links = mutableListOf(),
        lines = mutableMapOf()
    )

    fun addStreamOperationValue(
        type: String,
        direction: String,
        operationID: Int,
        elementID: Int,
        parentIDs: MutableList<Int>,
        value: String
    ) {
        // increment sequence counter if dircetion is not IN, because IN operations are not needed in the visualization later
        val streamOperationValue = StreamOperationValue(
            if(direction != "IN") sequenceCounter++ else 0, type, direction, operationID, elementID, parentIDs, value
        )
        streamtrace.add(streamOperationValue)
        if (direction != "OUT") {
            if (type == "sorted") {
                sortedTrace.add(streamOperationValue)
            }
            lastInOps[operationID] = streamOperationValue
        }
    }

    fun traceStartStream(
        type: String,
        operationID: Int,
        value: String
    ) {
        addStreamOperationValue(type, "START", operationID, elementcounter, mutableListOf(elementcounter), value)
        elementcounter++
    }

    fun traceInStream(
        type: String,
        operationID: Int,
        value: String
    ) {
        addStreamOperationValue(type, "IN", operationID, lastTraceValue!!.elementID, lastTraceValue!!.parentIDs.toMutableList(), value)
    }

    fun traceOutStream(
        type: String,
        operationID: Int,
        value: String
    ) {
        var elemID = if (type == "flatMap" && lastTraceValue!!.value != value) {
            val id = elementcounter
            elementcounter++
            id
        } else {
            lastTraceValue!!.elementID
        }
        var parentIDs = lastTraceValue!!.parentIDs
        if (type == "sorted") {
            val nextSorted = sortedTrace.find { it.value == value }
            parentIDs = nextSorted!!.parentIDs
            elemID = nextSorted.elementID
            sortedTrace.remove(nextSorted)
        }
        addStreamOperationValue(type, "OUT", operationID, elemID, parentIDs.toMutableList(), value)
    }

    fun traceEndStream(
        type: String,
        operationID: Int
    ) {
        when (type) {
            "count" -> {
                val lastCountOp = lastInOps[operationID]
                val elemID = lastCountOp?.elementID ?: elementcounter
                // parentIDs is the list of parent IDs of the last count operation plus the current element ID or the last trace value's element ID
                val parentIDs = (lastCountOp?.parentIDs ?: mutableListOf()).toMutableList().apply { add (lastTraceValue!!.elementID) }
                val count = (lastCountOp?.value?.toIntOrNull()?.plus(1)) ?: 1
                addStreamOperationValue(type, "END", operationID, elemID, parentIDs, count.toString())
            }
            "max" -> {
                val lastInOp = lastInOps[operationID]
                val newMax = lastInOp == null || lastInOp.value.toDouble() < lastTraceValue!!.value.toDouble()
                if (newMax) {
                    addStreamOperationValue(type, "END", operationID, lastTraceValue!!.elementID, lastTraceValue!!.parentIDs.toMutableList(), lastTraceValue!!.value)
                } else {
                    addStreamOperationValue(type, "END", operationID, lastInOp!!.elementID, lastInOp.parentIDs.toMutableList(), lastInOp.value)
                }
            }
            "min" -> {
                val lastInOp = lastInOps[operationID]
                val newMin = lastInOp == null || lastInOp.value.toDouble() > lastTraceValue!!.value.toDouble()
                if (newMin) {
                    addStreamOperationValue(type, "END", operationID, lastTraceValue!!.elementID, lastTraceValue!!.parentIDs.toMutableList(), lastTraceValue!!.value)
                } else {
                    addStreamOperationValue(type, "END", operationID, lastInOp!!.elementID, lastInOp.parentIDs.toMutableList(), lastInOp.value)
                }
            }
            else -> {
                addStreamOperationValue(type, "END", operationID, lastTraceValue!!.elementID, lastTraceValue!!.parentIDs.toMutableList(), "")
            }
        }
    }

    fun collectAndTransformStreamOperationValues() : StreamVisualizationObjects {
        // transform the streamtrace into a list of MarbleNodes, links and lines
        // Don't iterate, store marbles because of performance issues
        val nodes = visualizationObjects.marbles
        val links = visualizationObjects.links
        val lines = visualizationObjects.lines
        val nextop = streamtrace.lastOrNull()
        if (nextop != null) {
            if (!lines.containsKey(nextop.operationID)) {
                lines[nextop.operationID] = StreamOperationLine(nextop.type, lines.size * 100 + 50, lines.size * 100 + 50)
            }
            if (nextop.seq > 0) {
                val x = nextop.seq * 100 - lines[nextop.operationID]!!.x
                val y = lines[nextop.operationID]!!.y
                val elemId = "${nextop.elementID}.${nextop.operationID}"
                val parents = nextop.parentIDs.mapNotNull { parentId ->
                    nodes.find { it.elemId == "$parentId.${nextop.operationID + 1}" }
                }
                if (parents.isNotEmpty()) {
                    parents.forEach { x -> links.add(StreamLink(x.elemId, elemId)) }
                }
                nodes.add(StreamMarbleNode(nextop.seq, elemId, x, y, nextop.value, nextop.operationID, nextop.type, nextop.elementID))
            }
        }
        println(nodes)
        println(links)
        println(lines)
        return visualizationObjects
    }
}

data class StreamOperationValue (
    val seq: Int,
    val type: String,
    val direction: String,
    val operationID: Int,
    val elementID: Int,
    val parentIDs: List<Int>,
    val value: String,
)

data class StreamMarbleNode (
    val id: Int,
    val elemId: String,
    val x: Int,
    val y: Int,
    val label: String,
    val operationID: Int,
    val type: String,
    val color: Int
)

data class StreamLink (
    val source: String,
    val target: String
)

data class StreamOperationLine (
    val type: String,
    val y: Int,
    val x: Int
)

data class StreamVisualizationObjects (
    val marbles : MutableList<StreamMarbleNode>,
    val links : MutableList<StreamLink>,
    val lines : MutableMap<Int, StreamOperationLine>
)