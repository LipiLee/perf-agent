import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreException
import com.google.cloud.firestore.FirestoreOptions
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.lang.IllegalStateException
import java.time.Instant

data class VirtualMemorySize(val time: Instant, val size: Int)

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

            updateVirtualMemorySizeData(db, hostname, VirtualMemorySize(Instant.now(), virtualMemorySize))
        } else {
            // CRITICAL ALERT
        }
        Thread.sleep(interval)
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
    val value = input.readLine() ?: return 0

    return value.replace("\\s".toRegex(), "").toInt()
}

private fun sendVirtualMemorySizeData(db: Firestore, hostname: String, value: VirtualMemorySize) {
    val documentReference = db.collection("VirtualMemory").document(hostname)

    documentReference.set(value)
}

private fun updateVirtualMemorySizeData(db: Firestore, hostname: String, value: VirtualMemorySize) {
    val documentReference = db.collection("VirtualMemory").document(hostname)

    try {
        documentReference.update("time", value.time)
        documentReference.update("size", value.size)
    } catch (e: FirestoreException) {
        sendVirtualMemorySizeData(db, hostname, value)
    }

}
