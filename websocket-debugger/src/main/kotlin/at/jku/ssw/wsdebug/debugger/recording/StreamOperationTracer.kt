package at.jku.ssw.wsdebug.debugger.recording

import java.awt.Color
import kotlin.math.absoluteValue

class StreamOperationTracer {
    var streamtrace = mutableMapOf<Int, MutableList<StreamOperationValue>>()
    var actualStreamID = 0
    val lastTraceValue: StreamOperationValue?
        get() = streamtrace[actualStreamID]?.lastOrNull()
    var lastInOps = mutableMapOf<Int, MutableMap<Int, StreamOperationValue>>()
    var sortedTrace = mutableListOf<StreamOperationValue>()

    var sequenceCounter = mutableMapOf<Int, Int>()
    var elementcounter = 1

    var visualizationObjects = StreamVisualizationObjects(
        marbles = mutableListOf(),
        links = mutableListOf(),
        lines = mutableMapOf(),
        lastx = 50,
        lastopID = Int.MAX_VALUE
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
        val seq = if (direction != "IN") {
            val newSeq = sequenceCounter[actualStreamID]?.plus(1) ?: 1
            sequenceCounter[actualStreamID] = newSeq
            newSeq
        } else {
            0
        }
        val streamOperationValue = StreamOperationValue(
            seq, type, direction, operationID, elementID, parentIDs, value
        )
        streamtrace[actualStreamID]?.add(streamOperationValue)
        if (direction != "OUT") {
            if (type == "sorted") {
                sortedTrace.add(streamOperationValue)
            }
            lastInOps.getOrPut(actualStreamID) { mutableMapOf() }[operationID] = streamOperationValue
        }
    }

    fun traceStartStream(
        type: String,
        operationID: Int,
        value: String,
        streamId: Int
    ) {
        actualStreamID = streamId
        if (!sequenceCounter.containsKey(streamId)) {
            sequenceCounter[streamId] = 0
            streamtrace[streamId] = mutableListOf()
        }
        addStreamOperationValue(type, "START", operationID, elementcounter, mutableListOf(elementcounter), value)
        elementcounter++
    }

    fun traceInStream(
        type: String,
        operationID: Int,
        value: String,
        streamId: Int
    ) {
        actualStreamID = streamId
        addStreamOperationValue(type, "IN", operationID, lastTraceValue!!.elementID, mutableListOf(lastTraceValue!!.elementID), value)//lastTraceValue!!.parentIDs.toMutableList(),
    // value)
    }

    fun traceOutStream(
        type: String,
        operationID: Int,
        value: String,
        streamId: Int
    ) {
        actualStreamID = streamId
        var elemID = if (type == "flatMap" && lastInOps[actualStreamID]?.get(operationID)!!.value != value) {
            val id = elementcounter
            elementcounter++
            id
        } else {
            lastTraceValue!!.elementID
        }
        var parentIDs = lastInOps[actualStreamID]?.get(operationID)!!.parentIDs//lastTraceValue!!.parentIDs
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
        operationID: Int,
        streamId: Int
    ) {
        actualStreamID = streamId
        when (type) {
            "count" -> {
                val lastCountOp = lastInOps[actualStreamID]?.get(operationID)
                val elemID = lastCountOp?.elementID ?: elementcounter.also { elementcounter++ }
                // parentIDs is the list of parent IDs of the last count operation plus the current element ID or the last trace value's element ID
                val parentIDs = (lastCountOp?.parentIDs ?: mutableListOf()).toMutableList().apply { add (lastTraceValue!!.elementID) }
                val count = (lastCountOp?.value?.toIntOrNull()?.plus(1)) ?: 1
                addStreamOperationValue(type, "END", operationID, elemID, parentIDs, count.toString())
            }
            "max" -> {
                val lastInOp = lastInOps[operationID]
                val newMax = lastInOp == null || lastInOp[actualStreamID]!!.value.toDouble() < lastTraceValue!!.value.toDouble()
                if (newMax) {
                    addStreamOperationValue(type, "END", operationID, lastTraceValue!!.elementID, lastTraceValue!!.parentIDs.toMutableList(), lastTraceValue!!.value)
                } else {
                    addStreamOperationValue(type, "END", operationID, lastInOp?.get(actualStreamID)!!.elementID, lastInOp[actualStreamID]!!.parentIDs.toMutableList(),
                        lastInOp[actualStreamID]!!.value)
                }
            }
            "min" -> {
                val lastInOp = lastInOps[operationID]
                val newMin = lastInOp == null || lastInOp[actualStreamID]!!.value.toDouble() > lastTraceValue!!.value.toDouble()
                if (newMin) {
                    addStreamOperationValue(type, "END", operationID, lastTraceValue!!.elementID, lastTraceValue!!.parentIDs.toMutableList(), lastTraceValue!!.value)
                } else {
                    addStreamOperationValue(type, "END", operationID, lastInOp?.get(actualStreamID)!!.elementID, lastInOp[actualStreamID]!!.parentIDs.toMutableList(),
                        lastInOp[actualStreamID]!!.value)
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
        val nextop = streamtrace[actualStreamID]?.lastOrNull()
        var lastopID = visualizationObjects.lastopID
        if (nextop != null) {
            if (!lines.containsKey(nextop.operationID)) {
                lines[nextop.operationID] = StreamOperationLine(nextop.type, lines.size * 100 + 50)
            }
            if (nextop.seq > 0) {
                val x = if (nextop.operationID < lastopID) {
                    visualizationObjects.lastx
                } else {
                    visualizationObjects.lastx += 100
                    visualizationObjects.lastx
                }
                visualizationObjects.lastopID = nextop.operationID
                val y = lines[nextop.operationID]!!.y
                val elemId = "${nextop.elementID}.${nextop.operationID}"
                val parents = nextop.parentIDs.mapNotNull { parentId ->
                    nodes.find { it.elemId == "$parentId.${nextop.operationID + 1}" }
                }
                if (parents.isNotEmpty()) {
                    // If elemId is already in the links, visibleAt is plus one, else it is the current sequence
                    val visibleAt = //if (links.any { it.source == elemId || it.target == elemId }) {
                        //nextop.seq + 1
                    //} else {
                        nextop.seq
                    //}
                    parents.forEach { x -> links.add(StreamLink(x.elemId, elemId, visibleAt)) }
                }
                nodes.add(StreamMarbleNode(nextop.seq, elemId, x, y, nextop.value, nextop.operationID, nextop.type, getMarbleColor(nextop.elementID)))
            }
        }
        println(visualizationObjects.marblesToJson())
        println(visualizationObjects.linksToJson())
        println(visualizationObjects.linesToJson())
        return visualizationObjects
    }

    private fun getMarbleColor(value: Int): String {
        val goldenAngle = 137.508  // idealer Abstand in Grad (laut ChatGPT)
        val hue = ((value.hashCode().absoluteValue * goldenAngle) % 360).toFloat() / 360f
        val saturation = 0.6f
        val brightness = 0.95f

        val color = Color.getHSBColor(hue, saturation, brightness)
        return "#%02x%02x%02x".format(color.red, color.green, color.blue)
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

data class StreamMarbleNode(
    val id: Int,
    val elemId: String,
    val x: Int,
    val y: Int,
    val label: String,
    val operationID: Int,
    val type: String,
    val color: String
) {

}

data class StreamLink (
    val source: String,
    val target: String,
    val visibleAt: Int
)

data class StreamOperationLine (
    val type: String,
    val y: Int
)

data class StreamVisualizationObjects (
    val marbles : MutableList<StreamMarbleNode>,
    val links : MutableList<StreamLink>,
    val lines : MutableMap<Int, StreamOperationLine>,
    var lastx : Int,
    var lastopID: Int
) {
    fun reset() {
        marbles.clear()
        links.clear()
        lines.clear()
        lastx = 50
        lastopID = Int.MAX_VALUE
    }

    fun marblesToJson(): String {
        return marbles.joinToString(separator = ",", prefix = "[", postfix = "]") {
            """{"id": "${it.elemId}", "x": ${it.x}, "y": ${it.y}, "label": "${it.label}", "operationID": ${it.operationID}, "type": "${it.type}", "color": 
                |"${it.color}", "step": ${it.id}}""".trimMargin()
        }
    }

    fun linksToJson(): String {
        return links.joinToString(separator = ",", prefix = "[", postfix = "]") {
            """{"source": "${it.source}", "target": "${it.target}", "step": ${it.visibleAt}}"""
        }
    }

    fun linesToJson(): String {
        return lines.entries.joinToString(separator = ",", prefix = "[", postfix = "]") {
            """{"type": "${it.value.type}", "y": ${it.value.y}}"""
        }
    }
}