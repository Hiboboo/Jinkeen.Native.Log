package com.jinkeen.lifeplus.log.listener

interface OnLogProtocolStatusListener {

    /**
     * 本地日志操作各项功能与底层`C`代码的协议状态监听
     *
     * @param cmd 被调用的`C`代码块名称
     * @param code 执行`C`代码块的结果码
     */
    fun onProtocolStatus(cmd: String, code: Int)
}