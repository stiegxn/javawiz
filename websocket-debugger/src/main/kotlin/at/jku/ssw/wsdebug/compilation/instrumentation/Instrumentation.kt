package at.jku.ssw.wsdebug.compilation.instrumentation

import at.jku.ssw.wsdebug.compilation.JAVAWIZ_CLASS
import at.jku.ssw.wsdebug.compilation.JAVAWIZ_PACKAGE

data class Insert(val line: Int, val column: Int, val text: String)

fun modificationPoints(conditions: List<Condition>, arrayAccessIndexWrappers: List<IndexWrapper>, streamOperation: List<StreamOperation>): List<Insert> {
    return conditions.flatMap {
        return@flatMap listOf(
            Insert(it.beginLine, it.beginColumn, "$JAVAWIZ_PACKAGE.$JAVAWIZ_CLASS.recordCondition("),
            Insert(it.endLine, it.endColumn, "," + it.id + ")")
        )
    } + arrayAccessIndexWrappers.flatMap {
        return@flatMap listOf(
            Insert(it.beginLine, it.beginColumn, "$JAVAWIZ_PACKAGE.$JAVAWIZ_CLASS.recordArrayAccess(" + it.outerIndexedVariableName + ","),
            Insert(it.endLine, it.endColumn, "," + it.accessID + "," + it.dimension + ")")
        )
    } + streamOperation.flatMap {
        val inserts = mutableListOf<Insert>()
        if (it.name == "stream") {
            inserts.add(
                Insert(
                    it.endLine, it.endColumn, ".peek(x -> $JAVAWIZ_PACKAGE.$JAVAWIZ_CLASS.traceStream(\"START\", x, \"" + it.name + "\", " + it.id + ", " + it
                        .streamID + ", \"" + it.param
                            + "\"))"
                )
            )
        } else {
            // Glücklicherweise ist die ID immer 0, wenn es sich um die Terminal-Operation handelt.
            if (it.id == 0) {
                if (it.hasParam) {
                    inserts.add(Insert(it.beginLine, it.beginColumn, "$JAVAWIZ_PACKAGE.$JAVAWIZ_CLASS.traceParam("))
                    inserts.add(Insert(it.endLine, it.endColumn, "); $JAVAWIZ_PACKAGE.$JAVAWIZ_CLASS.collectAndTransformStreamOperationValues()"))
                } else {
                    inserts.add(Insert(it.beginLine, it.beginColumn, ".peek(x -> $JAVAWIZ_PACKAGE.$JAVAWIZ_CLASS.traceStream(\"END\", x, \"" + it.name + "\", 0, "
                    + it.streamID + ", \"" + it.param + "\")" +
                            ")"))
                    inserts.add(Insert(it.endLine, it.endColumn, "; $JAVAWIZ_PACKAGE.$JAVAWIZ_CLASS.collectAndTransformStreamOperationValues()"))
                }
            } else {
                inserts.add(
                    Insert(
                        it.beginLine,
                        it.beginColumn,
                        ".peek(x -> $JAVAWIZ_PACKAGE.$JAVAWIZ_CLASS.traceStream(\"IN\", x, \"" + it.name + "\", " + it.id + ", " + it.streamID + ", \"" + it.param + "\"))"
                    )
                )
                inserts.add(
                    Insert(
                        it.endLine,
                        it.endColumn,
                        ".peek(x -> $JAVAWIZ_PACKAGE.$JAVAWIZ_CLASS.traceStream(\"OUT\", x, \"" + it.name + "\", " + it.id + ", " + it.streamID + ", \"" + it.param + "\"))"
                    )
                )
            }
        }
        return@flatMap inserts
    }
}

fun applyModifications(original: String, modificationPoints: List<Insert>): String {
    val code = original.lines().toMutableList()
    modificationPoints
        .sortedWith(
            Comparator.comparing(Insert::line)
                .thenComparing(Insert::column)
                .reversed()
        ).forEach { point ->
            code[point.line] = code[point.line].substring(0, point.column) + point.text + code[point.line].substring(point.column)
        }
   return code.joinToString("\n")
}