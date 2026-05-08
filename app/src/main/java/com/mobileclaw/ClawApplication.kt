package com.mobileclaw

import android.app.Application
import com.mobileclaw.agent.RoleManager
import kotlinx.coroutines.flow.MutableSharedFlow
import com.mobileclaw.app.MiniAppStore
import com.mobileclaw.config.AgentConfig
import com.mobileclaw.config.SkillLevelStore
import com.mobileclaw.config.SkillNotesStore
import com.mobileclaw.config.UserConfig
import com.mobileclaw.llm.OpenAiGateway
import com.mobileclaw.memory.ConversationMemory
import com.mobileclaw.memory.SemanticMemory
import com.mobileclaw.memory.UserProfileExtractor
import com.mobileclaw.memory.db.ClawDatabase
import com.mobileclaw.perception.VirtualDisplayManager
import com.mobileclaw.permission.PermissionManager
import com.mobileclaw.server.ConsoleServer
import com.mobileclaw.server.LocalApiServer
import com.mobileclaw.skill.SkillLoader
import com.mobileclaw.skill.SkillRegistry
import com.mobileclaw.ui.AgentOverlayManager
import com.mobileclaw.ui.AuroraOverlayManager
import com.mobileclaw.ui.InAppWebViewManager
import com.mobileclaw.ui.aipage.AiPageStore

class ClawApplication : Application() {

    lateinit var database: ClawDatabase
        private set

    lateinit var skillRegistry: SkillRegistry
        private set

    lateinit var agentConfig: AgentConfig
        private set

    lateinit var overlayManager: AgentOverlayManager
        private set

    lateinit var auroraOverlayManager: AuroraOverlayManager
        private set

    lateinit var permissionManager: PermissionManager
        private set

    lateinit var semanticMemory: SemanticMemory
        private set

    lateinit var conversationMemory: ConversationMemory
        private set

    lateinit var userProfileExtractor: UserProfileExtractor
        private set

    lateinit var webViewManager: InAppWebViewManager
        private set

    lateinit var virtualDisplayManager: VirtualDisplayManager
        private set

    lateinit var userConfig: UserConfig
        private set

    lateinit var roleManager: RoleManager
        private set

    lateinit var localApiServer: LocalApiServer
        private set

    lateinit var miniAppStore: MiniAppStore
        private set

    lateinit var skillNotesStore: SkillNotesStore
        private set

    lateinit var skillLevelStore: SkillLevelStore
        private set

    lateinit var userStorageManager: com.mobileclaw.config.UserStorageManager
        private set

    lateinit var groupManager: com.mobileclaw.agent.GroupManager
        private set

    lateinit var consoleServer: ConsoleServer
        private set

    lateinit var aiPageStore: AiPageStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = ClawDatabase.getInstance(this)
        agentConfig = AgentConfig(this)
        applyLanguage(agentConfig.language)
        skillRegistry = SkillRegistry()
        overlayManager = AgentOverlayManager(this)
        auroraOverlayManager = AuroraOverlayManager(this)
        permissionManager = PermissionManager(this)
        semanticMemory = SemanticMemory(database.semanticDao())
        conversationMemory = ConversationMemory(database.conversationDao())
        userProfileExtractor = UserProfileExtractor(OpenAiGateway(agentConfig), semanticMemory, conversationMemory)
        webViewManager = InAppWebViewManager(this)
        virtualDisplayManager = VirtualDisplayManager(this)
        userConfig = UserConfig(this)
        roleManager = RoleManager(this)
        val sharedSkillLoader = SkillLoader(this, skillRegistry)
        localApiServer = LocalApiServer(
            skillRegistry = skillRegistry,
            skillLoader = sharedSkillLoader,
            semanticMemory = semanticMemory,
            userConfig = userConfig,
        )
        localApiServer.start()
        miniAppStore = MiniAppStore(this)
        skillNotesStore = SkillNotesStore(this)
        skillLevelStore = SkillLevelStore(this)
        userStorageManager = com.mobileclaw.config.UserStorageManager(this)
        groupManager = com.mobileclaw.agent.GroupManager(this)
        consoleServer = ConsoleServer(
            filesDir = filesDir,
            database = database,
            skillRegistry = skillRegistry,
            skillLoader = sharedSkillLoader,
            semanticMemory = semanticMemory,
            userConfig = userConfig,
        )
        consoleServer.start()
        aiPageStore = AiPageStore(filesDir)
    }

    override fun onTerminate() {
        super.onTerminate()
        localApiServer.stop()
        consoleServer.stop()
    }

    /** Tasks submitted from MiniAppActivity to the main agent. */
    val pendingAgentTask = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Context with the in-app language applied — use this for getString() calls. */
    var localizedContext: android.content.Context = this
        private set

    fun applyLanguage(language: String) {
        localizedContext = if (language == "auto" || language.isBlank()) {
            this
        } else {
            val locale = java.util.Locale.forLanguageTag(language)
            val config = android.content.res.Configuration(resources.configuration)
            config.setLocale(locale)
            createConfigurationContext(config)
        }
    }

    companion object {
        lateinit var instance: ClawApplication
            private set
    }
}
