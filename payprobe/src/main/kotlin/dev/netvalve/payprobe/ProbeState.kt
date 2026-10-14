package dev.netvalve.payprobe

/**
 * The run that is happening (or just happened), shared between the service that measures and
 * the screen that shows it. Both live in the same process, so a plain thread-safe object is enough.
 */
object ProbeState {
    private val lock = Any()
    private val samples = ArrayList<ProbeSample>()

    @Volatile var running: Boolean = false
        private set
    @Volatile var url: String = ""
        private set
    @Volatile var label: String = ""
        private set
    @Volatile var csvPath: String = ""
        private set
    @Volatile var startedWallMs: Long = 0L
        private set

    fun begin(url: String, label: String, csvPath: String, nowMs: Long) {
        synchronized(lock) {
            samples.clear()
            this.url = url
            this.label = label
            this.csvPath = csvPath
            this.startedWallMs = nowMs
            running = true
        }
    }

    fun add(sample: ProbeSample) {
        synchronized(lock) { samples.add(sample) }
    }

    fun end() {
        running = false
    }

    /** Forget the in-memory samples (the CSV file stays on disk). Ignored while a run is active. */
    fun clear() {
        synchronized(lock) { if (!running) samples.clear() }
    }

    fun all(): List<ProbeSample> = synchronized(lock) { ArrayList(samples) }

    fun last(n: Int): List<ProbeSample> = synchronized(lock) { ArrayList(samples.takeLast(n)) }

    fun summary(): ProbeSummary = ProbeStats.summarize(all())
}
