package com.mobileclaw.ui.aipage

/**
 * Evaluates ${expr} template strings in AI page component values and action steps.
 *
 * Supported expressions:
 *   ${state.key}         — page state value
 *   ${input.key}         — current input field value
 *   ${result.key}        — last action result field
 *   ${state.x + 1}       — basic arithmetic (int/float)
 *   ${state.x > 5}       — comparison (returns "true"/"false")
 *   ${state.x == "hi"}   — equality check
 */
object ExprEval {

    private val PLACEHOLDER = Regex("\\$\\{([^}]+)\\}")

    fun eval(
        template: String,
        state: Map<String, String>,
        input: Map<String, String>,
        result: Map<String, Any> = emptyMap(),
    ): String = template.replace(PLACEHOLDER) { mr ->
        resolve(mr.groupValues[1].trim(), state, input, result)
    }

    private fun resolve(
        expr: String,
        state: Map<String, String>,
        input: Map<String, String>,
        result: Map<String, Any>,
    ): String {
        // Simple property access
        if (expr.startsWith("state.") && !hasOperator(expr)) {
            return state[expr.removePrefix("state.")] ?: ""
        }
        if (expr.startsWith("input.") && !hasOperator(expr)) {
            return input[expr.removePrefix("input.")] ?: ""
        }
        if (expr.startsWith("result.") && !hasOperator(expr)) {
            return result[expr.removePrefix("result.")]?.toString() ?: ""
        }

        // Comparison: state.x > 5, state.x == "val", etc.
        val cmpResult = tryComparison(expr, state, input, result)
        if (cmpResult != null) return cmpResult

        // Basic arithmetic: state.count + 1, state.score * 2
        val arithResult = tryArithmetic(expr, state, input, result)
        if (arithResult != null) return arithResult

        return expr
    }

    private fun hasOperator(expr: String) =
        expr.contains('+') || expr.contains('-') || expr.contains('*') ||
            expr.contains('/') || expr.contains('>') || expr.contains('<') ||
            expr.contains("==") || expr.contains("!=")

    private fun tryComparison(expr: String, state: Map<String, String>, input: Map<String, String>, result: Map<String, Any>): String? {
        val ops = listOf("==", "!=", ">=", "<=", ">", "<")
        for (op in ops) {
            val idx = expr.indexOf(op)
            if (idx < 0) continue
            val left = expr.substring(0, idx).trim()
            val right = expr.substring(idx + op.length).trim()
            val lv = resolveLeaf(left, state, input, result)
            val rv = resolveLeaf(right, state, input, result)
            val r = lv.trim('"', '\'') to rv.trim('"', '\'')
            val lNum = lv.toDoubleOrNull()
            val rNum = rv.toDoubleOrNull()
            return when (op) {
                "==" -> (r.first == r.second).toString()
                "!=" -> (r.first != r.second).toString()
                ">"  -> if (lNum != null && rNum != null) (lNum > rNum).toString() else "false"
                "<"  -> if (lNum != null && rNum != null) (lNum < rNum).toString() else "false"
                ">=" -> if (lNum != null && rNum != null) (lNum >= rNum).toString() else "false"
                "<=" -> if (lNum != null && rNum != null) (lNum <= rNum).toString() else "false"
                else -> null
            } ?: continue
        }
        return null
    }

    private fun tryArithmetic(expr: String, state: Map<String, String>, input: Map<String, String>, result: Map<String, Any>): String? {
        val ops = listOf('+', '-', '*', '/')
        for (op in ops) {
            val idx = expr.lastIndexOf(op)
            if (idx <= 0) continue
            val left = expr.substring(0, idx).trim()
            val right = expr.substring(idx + 1).trim()
            val lv = resolveLeaf(left, state, input, result).toDoubleOrNull() ?: continue
            val rv = resolveLeaf(right, state, input, result).toDoubleOrNull() ?: continue
            val ans = when (op) {
                '+' -> lv + rv
                '-' -> lv - rv
                '*' -> lv * rv
                '/' -> if (rv != 0.0) lv / rv else Double.NaN
                else -> continue
            }
            return if (ans == ans.toLong().toDouble()) ans.toLong().toString() else ans.toString()
        }
        return null
    }

    private fun resolveLeaf(leaf: String, state: Map<String, String>, input: Map<String, String>, result: Map<String, Any>): String {
        if (leaf.startsWith("state.")) return state[leaf.removePrefix("state.")] ?: "0"
        if (leaf.startsWith("input.")) return input[leaf.removePrefix("input.")] ?: "0"
        if (leaf.startsWith("result.")) return result[leaf.removePrefix("result.")]?.toString() ?: "0"
        return leaf
    }
}
