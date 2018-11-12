import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.lang.IllegalStateException
import java.time.Instant

data class VirtualMemorySize(val hostname: String, val time: Instant, val size: Int)

fun main(args: Array<String>) {
    if (args.size != 3) {
        println("java -jar perf-agent.jar firebase-adminsdk.json hostname interval(millis)")
        return
    }

    val config = args[0]
    val hostname = args[1]
    val interval = args[2].toLong()
    val db = initializeFirestore(config)

    while (true) {
        val pid = getProcessId()
        if (pid != -1) {
            val virtualMemorySize = getVirtualMemorySize(pid)

            // Send data to server
//            println(virtualMemorySize)
            sendVirtualMemorySizeData(db, VirtualMemorySize(hostname, Instant.now(), virtualMemorySize))
        } else {
            // CRITICAL ALERT
        }
        Thread.sleep(interval)
//        Thread.sleep(60_000)
    }
}

private fun initializeFirestore(config: String): Firestore {
    val serviceAccount = FileInputStream(config)
    val options = FirestoreOptions.newBuilder().setTimestampsInSnapshotsEnabled(true)
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .build()

    return options.service
}

private fun getProcessId(): Int {
    try {
        return runCommand(listOf("pgrep", "ToyVpnServer"))
    } catch (e: IllegalStateException) {
        println("ToyVpnServer is not working.")
    }
    return -1
}

private fun getVirtualMemorySize(pid: Int): Int {
    return runCommand(listOf("ps", "h", "-o", "vsz", "-p", pid.toString()))
}

private fun runCommand(command: List<String>): Int {
    val builder = ProcessBuilder(command).redirectErrorStream(false)
    val process = builder.start()
    val input = BufferedReader(InputStreamReader(process.inputStream))
    val value = input.readLine().replace("\\s".toRegex(), "")

    return value.toInt()
}

private fun sendVirtualMemorySizeData(db: Firestore, value: VirtualMemorySize) {
    val collection = db.collection("VirtualMemory")

    collection.add(value)
}
