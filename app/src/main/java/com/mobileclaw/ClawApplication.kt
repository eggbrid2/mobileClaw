package com.mobileclaw

import android.app.Activity
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.mobileclaw.agent.RoleManager
import com.mobileclaw.agent.TaskRecipeStore
import com.mobileclaw.agent.TaskReplayStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.mobileclaw.app.MiniAppStore
import com.mobileclaw.config.AgentConfig
import com.mobileclaw.config.SkillLevelStore
import com.mobileclaw.config.SkillNotesStore
import com.mobileclaw.config.UserConfig
import com.mobileclaw.llm.HybridLlmGateway
import com.mobileclaw.llm.LocalGemmaGateway
import com.mobileclaw.llm.OpenAiGateway
import com.mobileclaw.llm.LocalModelManager
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
import com.mobileclaw.town.AgentTownStore
import com.mobileclaw.ui.AgentOverlayManager
import com.mobileclaw.ui.AuroraOverlayManager
import com.mobileclaw.ui.InAppWebViewManager
import com.mobileclaw.ui.MiniAppValidationOverlayManager
import com.mobileclaw.ui.aipage.AiPageStore
import com.mobileclaw.workspace.WorkspaceStore
import kotlinx.coroutines.runBlocking
import java.util.UUID

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

    lateinit var miniAppValidationOverlayManager: MiniAppValidationOverlayManager
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

    lateinit var agentTownStore: AgentTownStore
        private set

    lateinit var localModelManager: LocalModelManager
        private set

    lateinit var taskReplayStore: TaskReplayStore
        private set

    lateinit var taskRecipeStore: TaskRecipeStore
        private set

    lateinit var workspaceStore: WorkspaceStore
        private set

    private var startedActivityCount = 0
    private val _appForeground = MutableStateFlow(false)
    val appForeground: StateFlow<Boolean> = _appForeground.asStateFlow()

    fun isAppForeground(): Boolean = _appForeground.value

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerForegroundCallbacks()
        database = ClawDatabase.getInstance(this)
        agentConfig = AgentConfig(this)
        localModelManager = LocalModelManager(this)
        applyLanguage(agentConfig.language)
        skillRegistry = SkillRegistry()
        overlayManager = AgentOverlayManager(this)
        auroraOverlayManager = AuroraOverlayManager(this)
        miniAppValidationOverlayManager = MiniAppValidationOverlayManager(this)
        permissionManager = PermissionManager(this)
        semanticMemory = SemanticMemory(database.semanticDao())
        conversationMemory = ConversationMemory(database.conversationDao())
        userProfileExtractor = UserProfileExtractor(createLlmGateway(), semanticMemory, conversationMemory)
        webViewManager = InAppWebViewManager(this)
        virtualDisplayManager = VirtualDisplayManager(this)
        userConfig = UserConfig(this)
        roleManager = RoleManager(this)
        val consoleServerEnabled = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 || runBlocking {
            userConfig.get("console_server_enabled") == "true"
        }
        val consoleServerLanEnabled = runBlocking {
            userConfig.get("console_server_lan_enabled") == "true"
        }
        val consoleServerToken = if (consoleServerEnabled) {
            runBlocking {
                userConfig.get("console_server_token")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().also {
                    userConfig.set("console_server_token", it, "Auth token for ConsoleServer LAN access")
                }
            }
        } else {
            ""
        }
        val sharedSkillLoader = SkillLoader(this, skillRegistry)
        localApiServer = LocalApiServer(
            skillRegistry = skillRegistry,
            skillLoader = sharedSkillLoader,
            semanticMemory = semanticMemory,
            userConfig = userConfig,
        )
        localApiServer.start()
        miniAppStore = MiniAppStore(this)
        taskReplayStore = TaskReplayStore(filesDir)
        taskRecipeStore = TaskRecipeStore(filesDir)
        workspaceStore = WorkspaceStore(filesDir)
        skillNotesStore = SkillNotesStore(this)
        skillLevelStore = SkillLevelStore(this)
        userStorageManager = com.mobileclaw.config.UserStorageManager(this)
        groupManager = com.mobileclaw.agent.GroupManager(this)
        consoleServer = ConsoleServer(
            filesDir = filesDir,
            database = database,
            enabled = consoleServerEnabled,
            lanEnabled = consoleServerLanEnabled,
            authToken = consoleServerToken,
            skillRegistry = skillRegistry,
            skillLoader = sharedSkillLoader,
            semanticMemory = semanticMemory,
            userConfig = userConfig,
        )
        consoleServer.start()
        aiPageStore = AiPageStore(filesDir)
        agentTownStore = AgentTownStore(this)
    }

    fun createLlmGateway() = HybridLlmGateway(
        local = LocalGemmaGateway(this, localModelManager) { agentConfig.snapshot().localModelId },
        cloud = OpenAiGateway(agentConfig),
        useLocal = { agentConfig.snapshot().localModelEnabled },
        canUseCloud = { agentConfig.snapshot().let { it.endpoint.isNotBlank() && it.apiKey.isNotBlank() } },
        nativeOnly = { agentConfig.snapshot().localNativeOnly },
        localToolCallingEnabled = { agentConfig.snapshot().localToolCallingEnabled },
        language = { agentConfig.snapshot().language },
    )

    override fun onTerminate() {
        super.onTerminate()
        localApiServer.stop()
        consoleServer.stop()
    }

    private fun registerForegroundCallbacks() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                if (startedActivityCount == 1) _appForeground.value = true
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                if (startedActivityCount == 0) _appForeground.value = false
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
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
