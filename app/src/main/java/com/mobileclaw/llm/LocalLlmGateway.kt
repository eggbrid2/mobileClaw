package com.mobileclaw.llm

/** Stub for future local model integration (llama.cpp JNI / MLC-LLM). */
class LocalLlmGateway : LlmGateway {
    override suspend fun chat(request: ChatRequest): ChatResponse =
        throw NotImplementedError("Local LLM not implemented in this release")

    override suspend fun embed(text: String): FloatArray =
        throw NotImplementedError("Local embedding not implemented in this release")
}
