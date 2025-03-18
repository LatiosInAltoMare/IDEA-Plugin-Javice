package com.github.latiosinaltomare.firstplugin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "MyPluginSettings", storages = [Storage("MyPluginSettings.xml")])
@Service // 作为全局 ApplicationService
class MyPluginSettings : PersistentStateComponent<MyPluginSettings.State> {

    var myState = State()

    class State {
        var userInput: String = "" // 存储用户输入
    }

    override fun getState(): State {
        return myState
    }

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): MyPluginSettings {
            return com.intellij.openapi.application.ApplicationManager.getApplication().getService(MyPluginSettings::class.java)
        }
    }
}

