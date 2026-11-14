package com.freellm.chat.domain

object HistoryImages {
    fun keep(lastHasImages: Boolean, poolVision: List<Boolean>): Boolean =
        lastHasImages || (poolVision.isNotEmpty() && poolVision.all { it })

    fun prepare(history: List<ChatMsg>, keep: Boolean, placeholder: String): List<ChatMsg> {
        if (keep) return history
        return history.map { m ->
            if (m.images.isEmpty()) m else m.copy(images = emptyList(), content = placeholder + "\n" + m.content)
        }
    }
}

object ResumePolicy {
    fun skip(kind: FailKind, reasons: List<FailReason>): Boolean =
        kind == FailKind.NO_PROVIDER || kind == FailKind.NO_VISION || kind == FailKind.PINNED_MISSING ||
            reasons.any { it.error == ErrorKind.AUTH || it.error == ErrorKind.POLICY }
}

object BrainPolicy {
    const val FOLLOW_UP_CHARS = 80

    fun skipMemory(question: String, storedSize: Int): Boolean {
        val followUp = storedSize > 1
        if (!followUp) return false
        return question.trim().length <= FOLLOW_UP_CHARS || LocalBrain.contextDependent(question)
    }

    fun canLearn(question: String, answer: String, storedSize: Int, needsFresh: Boolean): Boolean =
        !skipMemory(question, storedSize) && !needsFresh && !OutputQuality.isPoor(answer)
}
