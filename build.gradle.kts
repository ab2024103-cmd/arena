plugins {
    id("com.android.application") version "8.7.2" apply false
    id("com.android.library") version "8.7.2" apply false
    kotlin("android") version "2.0.21" apply false
    kotlin("multiplatform") version "2.0.21" apply false
    kotlin("plugin.compose") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.7.0" apply false
    id("app.cash.sqldelight") version "2.0.2" apply false
}

// ============================================================================
// CI diagnostics (GitHub Actions annotations)
// ----------------------------------------------------------------------------
// The Actions log blob store is not reachable from the agent sandbox, so any
// compiler error ("e: ..."), failed test ("... FAILED"), exception header, or
// build-failure cause captured from task output is re-emitted as an ::error::
// workflow command. The runner converts those into check-run annotations,
// which are readable through the Checks API. Outside Actions they are inert.
// ============================================================================
val ciCaptured = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentLinkedQueue<String>>()

allprojects {
    tasks.configureEach {
        val task = this
        task.doFirst {
            val buf = java.util.concurrent.ConcurrentLinkedQueue<String>()
            ciCaptured[task.path] = buf
            task.logging.addStandardErrorListener { msg -> if (msg.length < 100_000) buf.add(msg.toString()) }
            task.logging.addStandardOutputListener { msg -> if (msg.length < 100_000) buf.add(msg.toString()) }
        }
    }
}

gradle.buildFinished { result ->
    if (result.failure == null) return@buildFinished

    val interesting = java.util.LinkedHashSet<String>()
    ciCaptured.forEach { (_, q) ->
        q.forEach { chunk ->
            chunk.lines().forEach { raw ->
                val line = raw.trim()
                val interestingLine =
                    line.startsWith("e: ") ||
                        line.contains(" FAILED") ||
                        line.startsWith("FAILURE: ") ||
                        line.contains("Execution failed for task") ||
                        line.startsWith("error: ") ||
                        (line.contains("Exception") && line.contains(": ") && !line.startsWith("at ")) ||
                        (line.contains("Error") && line.contains(": ") && !line.startsWith("at ") && !line.startsWith("*"))
                if (interestingLine && line.isNotEmpty()) interesting.add(line.take(400))
            }
        }
    }

    var cause: Throwable? = result.failure
    var depth = 0
    while (cause != null && depth < 5) {
        interesting.add("CAUSE[$depth]: ${cause.message}".take(400))
        cause = cause.cause
        depth++
    }

    var count = 0
    for (line in interesting) {
        if (count >= 9) break
        println("::error::${line.take(380)}")
        count++
    }
    if (count == 0) println("::error::build failed but no matching output lines were captured")
}
