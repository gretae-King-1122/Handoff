package com.handoff.api

/** 配置存取。设置页写、hook 进程读（目标进程侧对 RemotePreferences 只读）。 */
interface ConfigRepository {
    fun load(): Config

    fun save(config: Config)

    /** 注册变更监听（热更新）；返回取消注册的句柄。 */
    fun observe(onChange: (Config) -> Unit): () -> Unit
}
