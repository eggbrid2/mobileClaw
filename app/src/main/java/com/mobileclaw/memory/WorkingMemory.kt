package com.mobileclaw.memory

import com.mobileclaw.agent.AgentStep

/**
 * Sliding-window context for the current task.
 * Keeps recent steps within a token budget; trims oldest non-anchor steps first.
 */
class WorkingMemory(private val tokenBudget: Int = 2000) {

    private val steps = ArrayDeque<AgentStep>()

    fun push(step: AgentStep) {
        steps.addLast(step)
        // Trim from index 1 (preserve step 0 = anchor) when over budget
        while (estimateTokens() > tokenBudget && steps.size > 1) {
            steps.removeAt(1)
        }
    }

    fun steps(): List<AgentStep> = steps.toList()

    fun clear() = steps.clear()

    /** 1 token ≈ 4 chars; images are base64 strings (~1.33x raw size) counted at full weight */
    private fun estimateTokens(): Int = steps.sumOf {
        minOf(it.thought.length, 800) / 4 +
        minOf(it.observation.length, 4000) / 4 +
        (it.imageBase64?.length?.div(4) ?: 0)
    }
}
