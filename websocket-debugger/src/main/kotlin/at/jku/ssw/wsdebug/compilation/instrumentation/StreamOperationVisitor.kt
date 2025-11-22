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
        if (lambdaLevel > 0) {
            // Skip method invocations inside lambdas
            super.visitApply(methodInvocation)
            return
        }
        val meth = methodInvocation.meth
        if (meth is JCTree.JCFieldAccess) {
            var beginLine: Int
            var beginColumn: Int
            var hasParam = false
            val receiverType = meth.selected.type?.tsym.toString()
            if (isStreamType(receiverType)) {
                val name = meth.name.toString()
                var firstArgAsString: String = ""
                if (name in terminalOperations) {
                    actualStreamID = ++numberOfStreams
                    openStreams.add(numberOfStreams)
                    streamOperations[actualStreamID] = mutableListOf()

                    if (methodInvocation.args.isNotEmpty()) {
                        hasParam = true
                        beginLine = pos.getBeginLineStreamOp(methodInvocation.args[0])
                        beginColumn = pos.getBeginColumn(methodInvocation.args[0]) - 1
                    } else {
                        beginLine = pos.getBeginLineStreamOp(meth)
                        beginColumn = pos.getBeginColumnStreamOp(meth)
                    }
                } else {
                    beginLine = pos.getBeginLineStreamOp(meth)
                    beginColumn = pos.getBeginColumnStreamOp(meth)
                }

                if (methodInvocation.args.isNotEmpty()) {
                    hasParam = true
                    firstArgAsString = methodInvocation.args[0].toString()
                    firstArgAsString = escapeForJavaString(firstArgAsString)
                }

                streamOperations[actualStreamID]!!.add(StreamOperation(
                    beginLine,
                    beginColumn,
                    pos.getEndLine(methodInvocation) - 1,
                    pos.getEndColumn(methodInvocation) - 1,
                    meth.name.toString(),
                    streamOperations[actualStreamID]!!.size,
                    hasParam,
                    firstArgAsString,
                    actualStreamID
                ))
            } else if (meth.name.toString() == "stream") {
                streamOperations[actualStreamID]!!.add(StreamOperation(
                    0,
                    0,
                    pos.getEndLine(methodInvocation) - 1,
                    pos.getEndColumn(methodInvocation) - 1,
                    "stream",
                    streamOperations[actualStreamID]!!.size,
                    false,
                    "",
                    actualStreamID
                ))
                openStreams.remove(actualStreamID)
                actualStreamID = openStreams.lastOrNull() ?: -1
            }
        }
        super.visitApply(methodInvocation)
    }

    private val streamOperations = mutableMapOf<Int, MutableList<StreamOperation>>()
    private val openStreams = mutableListOf<Int>()
    private var numberOfStreams = -1
    private var actualStreamID = -1
    private val terminalOperations = setOf("count", "max", "min", "reduce", "collect", "forEach", "toArray", "toList", "toSet", "findFirst", "findAny", "anyMatch", "allMatch", "noneMatch")
    private var lambdaLevel = 0

    fun getStreamOperations(): List<StreamOperation> {
        return streamOperations.values.flatten()//streamOperations.toList()
    }

    private fun isStreamType(type: String?): Boolean {
        if (type == null) return false

        return type == "java.util.stream.Stream" ||
                type == "java.util.stream.IntStream" ||
                type == "java.util.stream.LongStream" ||
                type == "java.util.stream.DoubleStream"
    }

    private fun escapeForJavaString(text: String): String {
        return text
            .replace("\\", "\\\\")   // erst Backslashes doppeln
            .replace("\"", "\\\"")   // dann Anführungszeichen escapen
            .replace("\r\n", "\\\\n") // Windows-Zeilenumbrüche
            .replace("\n", "\\\\n")   // Unix-Zeilenumbrüche
            .replace("\t", "\\\\t")   // Tabs
    }

    override fun visitLambda(tree: JCTree.JCLambda) {
        lambdaLevel++
        super.visitLambda(tree)
        lambdaLevel--
    }

    override fun visitReference(tree: JCTree.JCMemberReference) {
        lambdaLevel++
        super.visitReference(tree)
        lambdaLevel--
    }
}