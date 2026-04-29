package com.mobileclaw

import android.app.Application
import com.mobileclaw.agent.RoleManager
import com.mobileclaw.app.MiniAppStore
import com.mobileclaw.config.AgentConfig
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

    lateinit var userStorageManager: com.mobileclaw.config.UserStorageManager
        private set

    lateinit var consoleServer: ConsoleServer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = ClawDatabase.getInstance(this)
        agentConfig = AgentConfig(this)
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
        localApiServer = LocalApiServer(
            skillRegistry = skillRegistry,
            skillLoader = SkillLoader(this, skillRegistry),
            semanticMemory = semanticMemory,
            userConfig = userConfig,
        )
        localApiServer.start()
        miniAppStore = MiniAppStore(this)
        skillNotesStore = SkillNotesStore(this)
        userStorageManager = com.mobileclaw.config.UserStorageManager(this)
        consoleServer = ConsoleServer(filesDir = filesDir, database = database)
        consoleServer.start()
    }

    override fun onTerminate() {
        super.onTerminate()
        localApiServer.stop()
        consoleServer.stop()
    }

    companion object {
        lateinit var instance: ClawApplication
            private set
    }
}
