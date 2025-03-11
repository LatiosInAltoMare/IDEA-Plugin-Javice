package com.github.latiosinaltomare.firstplugin.widget

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import org.jetbrains.annotations.NotNull

class MySimpleStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): @NotNull String {
        return "MySimpleStatusBarWidgetFactory"
    }

    override fun getDisplayName(): String {
        return "My Simple Button"
    }

    override fun isAvailable(project: Project): Boolean {
        return true  // 始终可用
    }

    override fun createWidget(project: Project): StatusBarWidget {
        return MySimpleStatusBarWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean {
        return true  // 在所有状态栏上都可以启用
    }
}
