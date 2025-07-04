@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package at.jku.ssw.wsdebug.compilation.instrumentation

import com.sun.tools.javac.tree.JCTree
import com.sun.tools.javac.tree.TreeScanner

fun generateStreamOps(tree: JCTree.JCCompilationUnit) : List<StreamOperation> {
    val visitor = StreamOperationVisitor(Positioning(tree))
    tree.accept(visitor)
    return visitor.getStreamOperations()
}

class StreamOperationVisitor(val pos: Positioning) : TreeScanner() {

    override fun visitApply(methodInvocation: JCTree.JCMethodInvocation) {
        val meth = methodInvocation.meth
        if (meth is JCTree.JCFieldAccess) {
            var beginLine: Int
            var beginColumn: Int
            var hasParam = false
            val receiverType = meth.selected.type?.tsym.toString()
            if (isStreamType(receiverType)) {
                val name = meth.name.toString()
                if (name == "max" || name == "min") {
                    if (methodInvocation.args.isNotEmpty()) {
                        hasParam = true
                        beginLine = pos.getBeginLineStreamOp(methodInvocation.args[0])
                        beginColumn = pos.getBeginColumn(methodInvocation.args[0])
                    } else {
                        beginLine = pos.getBeginLineStreamOp(meth)
                        beginColumn = pos.getBeginColumnStreamOp(meth)
                    }
                } else {
                    beginLine = pos.getBeginLineStreamOp(meth)
                    beginColumn = pos.getBeginColumnStreamOp(meth)
                }
                streamOperations.add(StreamOperation(
                    beginLine,
                    beginColumn,
                    pos.getEndLine(methodInvocation) - 1,
                    pos.getEndColumn(methodInvocation) - 1,
                    meth.name.toString(),
                    streamOperations.size,
                    hasParam
                ))
            } else if (meth.name.toString() == "stream") {
                streamOperations.add(StreamOperation(
                    0,
                    0,
                    pos.getEndLine(methodInvocation) - 1,
                    pos.getEndColumn(methodInvocation) - 1,
                    "stream",
                    streamOperations.size
                ))
            }
        }
        super.visitApply(methodInvocation)
    }

    private val streamOperations = mutableListOf<StreamOperation>()

    fun getStreamOperations(): List<StreamOperation> {
        return streamOperations.toList()
    }

    private fun isStreamType(type: String?): Boolean {
        if (type == null) return false

        return type == "java.util.stream.Stream" ||
                type == "java.util.stream.IntStream" ||
                type == "java.util.stream.LongStream" ||
                type == "java.util.stream.DoubleStream"
    }
}