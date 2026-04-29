package com.mobileclaw.memory

import com.mobileclaw.agent.AgentStep
import com.mobileclaw.llm.Message

/**
 * Sliding-window context for the current task.
 * Keeps the most recent steps within a token budget.
 */
class WorkingMemory(private val tokenBudget: Int = 4096) {

    private val steps = ArrayDeque<AgentStep>()

    fun push(step: AgentStep) {
        steps.addLast(step)
        // Trim from index 1 (preserve step 0 = first observation) when over budget
        while (estimateTokens() > tokenBudget && steps.size > 1) {
            steps.removeAt(1)
        }
    }

    fun steps(): List<AgentStep> = steps.toList()

    fun clear() = steps.clear()

    /** Rough token estimate: 1 token ≈ 4 chars */
    private fun estimateTokens(): Int =
        steps.sumOf { (it.thought.length + it.observation.length) / 4 }
}
