package at.jku.ssw.wsdebug.compilation.instrumentation

data class StreamOperation (
    val beginLine: Int, val beginColumn: Int,
    val endLine: Int, val endColumn: Int,
    val name: String, val id: Int, val hasParam : Boolean = false
)