package com.mobileclaw.memory

import com.mobileclaw.agent.AgentStep

/**
 * Sliding-window context for the current task.
 * Keeps recent steps within a token budget; trims oldest non-anchor steps first.
 */
class WorkingMemory(private val tokenBudget: Int = 6000) {

    private val steps = ArrayDeque<AgentStep>()

    fun push(step: AgentStep) {
        steps.addLast(step)
        // Keep the most recent execution trace. The original user goal is already
        // outside this window, so preserving an old first screenshot is worse than
        // preserving the latest actions and verification results.
        while (estimateTokens() > tokenBudget && steps.size > MIN_STEPS_TO_KEEP) {
            steps.removeFirst()
        }
    }

    fun steps(): List<AgentStep> = steps.toList()

    fun clear() = steps.clear()

    /** 1 token ≈ 4 chars. Images are sent separately; count them as a small context marker here. */
    private fun estimateTokens(): Int = steps.sumOf {
        minOf(it.thought.length, 800) / 4 +
        minOf(it.observation.length, 4000) / 4 +
        if (it.imageBase64.isNullOrBlank()) 0 else IMAGE_CONTEXT_COST
    }

    private companion object {
        const val MIN_STEPS_TO_KEEP = 8
        const val IMAGE_CONTEXT_COST = 256
    }
}
