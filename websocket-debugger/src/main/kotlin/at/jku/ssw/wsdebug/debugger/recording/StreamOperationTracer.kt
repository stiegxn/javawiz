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

    var visualizationObjects = StreamVisualizationInfo(
        marbles = mutableListOf(),
        links = mutableListOf(),
        operationLines = mutableMapOf(),
        lastX = 50,
        lastOpId = Int.MAX_VALUE
    )

    fun addStreamOperationValue(
        type: String,
        direction: String,
        operationID: Int,
        elementID: Int,
        parentIDs: MutableList<Int>,
        valuetype: String?,
        value: Any,
        param: String
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
            seq, type, direction, operationID, elementID, parentIDs, valuetype, value, param
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
        value: Any,
        valuetype: String?,
        streamId: Int
    ) {
        actualStreamID = streamId
        if (!sequenceCounter.containsKey(streamId)) {
            sequenceCounter[streamId] = 0
            streamtrace[streamId] = mutableListOf()
        }
        addStreamOperationValue(type, "START", operationID, elementcounter, mutableListOf(elementcounter), valuetype, value, "")
        elementcounter++
    }

    fun traceInStream(
        type: String,
        operationID: Int,
        value: Any,
        valuetype: String?,
        streamId: Int,
        param: String
    ) {
        actualStreamID = streamId
        addStreamOperationValue(type, "IN", operationID, lastTraceValue!!.elementID, mutableListOf(lastTraceValue!!.elementID), valuetype, value, param)//lastTraceValue!!.parentIDs
    // .toMutableList(),
    // value)
    }

    fun traceOutStream(
        type: String,
        operationID: Int,
        value: Any,
        valuetype: String?,
        streamId: Int,
        param: String
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
        addStreamOperationValue(type, "OUT", operationID, elemID, parentIDs.toMutableList(), valuetype, value, param)
    }

    fun traceEndStream(
        type: String,
        operationID: Int,
        streamId: Int,
        param: String
    ) {
        actualStreamID = streamId
        when (type) {
            "count" -> {
                val lastCountOp = lastInOps[actualStreamID]?.get(operationID)
                val elemID = lastCountOp?.elementID ?: elementcounter.also { elementcounter++ }
                // parentIDs is the list of parent IDs of the last count operation plus the current element ID or the last trace value's element ID
                val parentIDs = (lastCountOp?.parentIDs ?: mutableListOf()).toMutableList().apply { add (lastTraceValue!!.elementID) }
                val count = (lastCountOp?.value.let {
                    when (it) {
                        is Int -> it + 1
                        is String -> (it.toIntOrNull() ?: 0) + 1
                        else -> 1
                    }
                })
                addStreamOperationValue(type, "END", operationID, elemID, parentIDs, "int", count, param)
            }
            "collect",
            "toList", "toArray" -> {
                if (lastTraceValue == null) {
                    return
                }
                val lastListOp = lastInOps[actualStreamID]?.get(operationID)
                val elemID = lastListOp?.elementID ?: elementcounter.also { elementcounter++ }
                val parentIDs = (lastListOp?.parentIDs ?: mutableListOf()).toMutableList().apply { lastTraceValue?.let { add (it.elementID) } }
                val list = parentIDs.toMutableList()
//                val type = if (type == "toList") "List" else "Array"
                val type = when(type) {
                    "toList" -> "List"
                    "toArray" -> "Array"
                    "collect" -> {
                        // paramlower before first occurence of '('
                        val paramLower = param.substringBefore('(').lowercase()
                        when {
                            paramLower.contains("set") -> "Set"
                            paramLower.contains("list") -> "List"
                            paramLower.contains("array") -> "Array"
                            paramLower.contains("map") -> "Map"
                            else -> "Object"
                        }
                    }
                    else -> type
                }
                if (type == "Map") {
                    // for map, parentIDs are pairs of key and value IDs, so we need to group them
                    val mapEntries = mutableListOf<Pair<Int, Int>>()
                    for (i in list.indices step 2) {
                        if (i + 1 < list.size) {
                            mapEntries.add(Pair(list[i], list[i + 1]))
                        }
                    }
                    addStreamOperationValue(type, "END", operationID, elemID, parentIDs, type, mapEntries, param)
                    return
                }
                addStreamOperationValue(type, "END", operationID, elemID, parentIDs, type, list, param)
            }
//            "max" -> {
//                val lastInOp = lastInOps[operationID]
//                val newMax = lastInOp == null || lastInOp[actualStreamID]!!.value.toDouble() < lastTraceValue!!.value.toDouble()
//                if (newMax) {
//                    addStreamOperationValue(type, "END", operationID, lastTraceValue!!.elementID, lastTraceValue!!.parentIDs.toMutableList(), lastTraceValue!!.value)
//                } else {
//                    addStreamOperationValue(type, "END", operationID, lastInOp?.get(actualStreamID)!!.elementID, lastInOp[actualStreamID]!!.parentIDs.toMutableList(),
//                        lastInOp[actualStreamID]!!.value)
//                }
//            }
//            "min" -> {
//                val lastInOp = lastInOps[operationID]
//                val newMin = lastInOp == null || lastInOp[actualStreamID]!!.value.toDouble() > lastTraceValue!!.value.toDouble()
//                if (newMin) {
//                    addStreamOperationValue(type, "END", operationID, lastTraceValue!!.elementID, lastTraceValue!!.parentIDs.toMutableList(), lastTraceValue!!.value)
//                } else {
//                    addStreamOperationValue(type, "END", operationID, lastInOp?.get(actualStreamID)!!.elementID, lastInOp[actualStreamID]!!.parentIDs.toMutableList(),
//                        lastInOp[actualStreamID]!!.value)
//                }
//            }
            else -> {
                addStreamOperationValue(type, "END", operationID, lastTraceValue!!.elementID, lastTraceValue!!.parentIDs.toMutableList(), null,"", param)
            }
        }
    }

    fun traceNOPEndStream(operationID: Int) {
        val lastListOp = lastInOps[actualStreamID]?.get(operationID)!!
        val parentIDs = lastListOp.parentIDs.toMutableList().apply { lastTraceValue?.let { add (it.elementID) } }
        addStreamOperationValue(lastListOp.type, lastListOp.direction, lastListOp.operationID, lastListOp.elementID, parentIDs, lastListOp.valuetype, lastListOp.value, lastListOp.param)
    }
    fun collectAndTransformStreamOperationValues(): StreamVisualizationInfo {
        visualizationObjects.reset()
        val nodes = visualizationObjects.marbles
        val links = visualizationObjects.links
        val lines = visualizationObjects.operationLines

        val operations = streamtrace[actualStreamID] ?: mutableListOf()
        var lastopID = visualizationObjects.lastOpId
        var currentseq = 0
        var seqOffset = 0

        val sortedOperations = mutableSetOf<Int>()

        var lastValueType = ""
        var containsBigType = false
        for (op in operations) {
            if (!containsBigType && !arrayOf("int", "long", "double", "float", "boolean", "char", "byte", "short").contains(op.valuetype)) {
                containsBigType = true
            }
            if (!lines.containsKey(op.operationID)) {
                var yValue = (lines.values.maxByOrNull { it.y }?.y ?: -100) + 100
                println("YValue for operation ${op.operationID} (${op.type}): $yValue")
                if (yValue != 0) {
                    if (!arrayOf("int", "long", "double", "float", "boolean", "char", "byte", "short", "java.lang.String").contains(lastValueType)) {
                        yValue += 100
                    }
                }
                println("New YValue for operation ${op.operationID} (${op.type}): $yValue")
                lines[op.operationID] = StreamOperationLine(op.type, yValue, op.valuetype, op.param)
            }
            if (op.seq > 0 || (op.direction == "IN" && op.type == "filter")) {
                if (op.type == "filter") {
                    if (op.direction == "OUT") {
                        nodes.last().color = getMarbleColor(op)
                        nodes.last().direction = op.direction
                        seqOffset--
                        continue
                    } else {
                        seqOffset++
                        currentseq += 1
                    }
                } else {
                    currentseq = op.seq + seqOffset
                }
                if (op.type == "sorted" && !sortedOperations.contains(op.operationID)) {
                    visualizationObjects.lastX += if (!arrayOf("int", "long", "double", "float", "boolean").contains(lines[op.operationID]!!.valuetype)) {
                        200
                    } else {
                        100
                    }
                    sortedOperations.add(op.operationID)
                }
                val x = if (op.operationID < lastopID) {
                    visualizationObjects.lastX
                } else {
                    if (containsBigType) {
                        if (op.valuetype!!.endsWith("[]") &&
                            !arrayOf("int", "long", "double", "float", "boolean", "char", "byte", "short", "java.lang.String").contains(op.valuetype.removeSuffix("[]"))) {
                            visualizationObjects.lastX += 300
                        } else {
                            visualizationObjects.lastX += 200
                        }
                    } else {
                        visualizationObjects.lastX += 100
                    }
                    visualizationObjects.lastX
                }
                visualizationObjects.lastOpId = op.operationID
                val y = lines[op.operationID]!!.y
                val elemId = "${op.elementID}.${op.operationID}"
                val parents = op.parentIDs.mapNotNull { parentId ->
                    nodes.find { it.elemId == "$parentId.${op.operationID + 1}" }
                }
                if (parents.isNotEmpty()) {
                    val visibleAt = currentseq
                    parents.forEach { p -> links.add(StreamLink(p.elemId, elemId, visibleAt)) }
                }
                val label = when (op.valuetype) {
                    "List", "Array", "Set", "Map" -> parents.joinToString(", ") { p -> p.elemId }
                    else -> op.value.toString()
                }
                nodes.add(StreamMarble(currentseq, elemId, x, y, op.valuetype, label, op.operationID, op.type, getMarbleColor(op), op.direction))
                lastopID = op.operationID
                lastValueType = op.valuetype ?: lastValueType
            }
        }

        println(visualizationObjects.marblesToJson())
        println(visualizationObjects.linksToJson())
        println(visualizationObjects.linesToJson())
        return visualizationObjects
    }

    private fun getMarbleColor(value: StreamOperationValue): String {
        if (value.direction == "IN") return "#FFFFFF"
        when (value.type) {
            "List" -> return "#DBDBDB"
            "Array" -> return "#CCEEFF"
            "Set" -> return "#FFDDCC"
            "Map" -> return "#FFCCCC"
            else -> {
                val goldenAngle = 137.508  // idealer Abstand in Grad (laut ChatGPT)
                val hue = ((value.elementID.hashCode().absoluteValue * goldenAngle) % 360).toFloat() / 360f
                val saturation = 0.6f
                val brightness = 0.95f

                val color = Color.getHSBColor(hue, saturation, brightness)
                return "#%02x%02x%02x".format(color.red, color.green, color.blue)
            }
        }
    }
}

data class StreamOperationValue (
    val seq: Int,
    val type: String,
    val direction: String,
    val operationID: Int,
    val elementID: Int,
    val parentIDs: List<Int>,
    val valuetype: String?,
    val value: Any,
    val param: String
)

data class StreamMarble(
    var id: Int,
    val elemId: String,
    val x: Int,
    val y: Int,
    val valuetype: String?,
    val label: String,
    val operationID: Int,
    val type: String,
    var color: String,
    var direction: String
) {

}

data class StreamLink (
    val source: String,
    val target: String,
    val visibleAt: Int
)

data class StreamOperationLine (
    val type: String,
    val y: Int,
    val valuetype: String?,
    val param: String?
)

data class StreamVisualizationInfo (
    val marbles : MutableList<StreamMarble>,
    val links : MutableList<StreamLink>,
    val operationLines : MutableMap<Int, StreamOperationLine>,
    var lastX : Int,
    var lastOpId: Int
) {
    fun reset() {
        marbles.clear()
        links.clear()
        operationLines.clear()
        lastX = 50
        lastOpId = Int.MAX_VALUE
    }

    fun marblesToJson(): String {
        return marbles.joinToString(separator = ",", prefix = "[", postfix = "]") {
            """{"id": "${it.elemId}", "x": ${it.x}, "y": ${it.y}, "valuetype": "${it.valuetype}, "label": "${it.label}", "operationID": ${it.operationID}, "type": "${it.type}", 
                |"color": 
                |"${it.color}", "step": ${it.id}}""".trimMargin()
        }
    }

    fun linksToJson(): String {
        return links.joinToString(separator = ",", prefix = "[", postfix = "]") {
            """{"source": "${it.source}", "target": "${it.target}", "step": ${it.visibleAt}}"""
        }
    }

    fun linesToJson(): String {
        return operationLines.entries.joinToString(separator = ",", prefix = "[", postfix = "]") {
            """{"type": "${it.value.type}", "y": ${it.value.y}}"""
        }
    }
}