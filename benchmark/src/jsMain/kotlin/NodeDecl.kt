import org.khronos.webgl.ArrayBufferView
import org.khronos.webgl.Uint8Array

external val process: Process
typealias Buffer = ArrayBufferView

external interface Process {
    val stdin: ReadStream
    val stdout: WriteStream
    fun exit(code: Int)
}

external interface WriteStream {
    fun write(arr: Uint8Array)

}

external interface ReadStream {
    fun on(where: String, onData: (Buffer) -> Unit)
}
