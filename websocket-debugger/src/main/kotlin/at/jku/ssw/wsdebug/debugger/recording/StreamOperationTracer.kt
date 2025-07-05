package at.jku.ssw.wsdebug.debugger.recording

class StreamOperationTracer {
    var streamtrace = mutableListOf<StreamOperationValue>()
    val lastTraceValue: StreamOperationValue?
        get() = streamtrace.lastOrNull()
    var lastInOps = mutableMapOf<Int, StreamOperationValue>()
    var sortedTrace = mutableListOf<StreamOperationValue>()

    var sequenceCounter = 1
    var elementcounter = 1

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

    fun collectAndTransformStreamOperationValues() : List<StreamMarbleNode> {
        // transform the streamtrace into a list of MarbleNodes

        val nodes = mutableListOf<StreamMarbleNode>()
        for (op in streamtrace) {
            val x = op.seq * 100 - 50
            val y = op.operationID * 100 + 50
            val parents = op.parentIDs.mapNotNull {
                parentId -> nodes.find { it.elemId == "$parentId.${op.operationID-1}" }
            }
            nodes.add(StreamMarbleNode(op.seq, "${op.elementID}.${op.operationID}", x, y, op.value, parents, op.operationID, op.type, op.elementID))
        }

        return nodes
    }
}

data class StreamOperationValue(
    val seq: Int,
    val type: String,
    val direction: String,
    val operationID: Int,
    val elementID: Int,
    val parentIDs: List<Int>,
    val value: String,
) { }

data class StreamMarbleNode(
    val id: Int,
    val elemId: String,
    val x: Int,
    val y: Int,
    val label: String,
    val parents: List<StreamMarbleNode>,
    val operationID: Int,
    val type: String,
    val color: Int,
) { }