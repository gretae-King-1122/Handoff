package com.handoff.api

/** hook 点来源，用于日志区分命中链路。 */
enum class Source {
    /** P0 `MiniAppController#startAppByLink` */
    CONTROLLER,

    /** P1 `MiniAppLauncher#openMiniApp` */
    LAUNCHER,

    /** P2 `AppBrandUI#onCreate` */
    CONTAINER,
}

/**
 * 层间唯一契约。
 *
 * 约束：
 * 1. 不含任何 `com.tencent.*`，不含 `android.*` —— 否则 `:module-rules` 无法纯 JVM 测试。
 * 2. 可空字段一律为 `null` 而不是空串（空串会让提取正则误命中）。
 * 3. Java 侧（hook 层）请用 [MiniAppEvent.builder]。
 */
data class MiniAppEvent(
    val source: Source,
    val processName: String,
    val miniAppId: String? = null,
    val name: String? = null,
    val entryPath: String? = null,
    val fakeUrl: String? = null,
    val link: String? = null,
    val linkType: Int? = null,
    val scene: Int? = null,
    val via: String? = null,
    val arkApp: String? = null,
    val meta: String? = null,
    val rawExtras: Map<String, String> = emptyMap(),
) {
    /** Java 友好的构造器。 */
    class Builder {
        private var source: Source = Source.CONTROLLER
        private var processName: String = ""
        private var miniAppId: String? = null
        private var name: String? = null
        private var entryPath: String? = null
        private var fakeUrl: String? = null
        private var link: String? = null
        private var linkType: Int? = null
        private var scene: Int? = null
        private var via: String? = null
        private var arkApp: String? = null
        private var meta: String? = null
        private var rawExtras: Map<String, String> = emptyMap()

        fun source(value: Source) = apply { source = value }
        fun processName(value: String) = apply { processName = value }
        fun miniAppId(value: String?) = apply { miniAppId = value }
        fun name(value: String?) = apply { name = value }
        fun entryPath(value: String?) = apply { entryPath = value }
        fun fakeUrl(value: String?) = apply { fakeUrl = value }
        fun link(value: String?) = apply { link = value }
        fun linkType(value: Int?) = apply { linkType = value }
        fun scene(value: Int?) = apply { scene = value }
        fun via(value: String?) = apply { via = value }
        fun arkApp(value: String?) = apply { arkApp = value }
        fun meta(value: String?) = apply { meta = value }
        fun rawExtras(value: Map<String, String>) = apply { rawExtras = value }

        fun build(): MiniAppEvent = MiniAppEvent(
            source = source,
            processName = processName,
            miniAppId = miniAppId,
            name = name,
            entryPath = entryPath,
            fakeUrl = fakeUrl,
            link = link,
            linkType = linkType,
            scene = scene,
            via = via,
            arkApp = arkApp,
            meta = meta,
            rawExtras = rawExtras,
        )
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
