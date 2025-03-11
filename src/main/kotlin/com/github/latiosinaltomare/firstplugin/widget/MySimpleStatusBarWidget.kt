package com.github.latiosinaltomare.firstplugin.widget

import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Consumer
import org.jetbrains.annotations.NotNull
import javax.swing.JOptionPane
import java.awt.event.MouseEvent
import com.intellij.openapi.project.Project
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.*
import com.intellij.openapi.editor.markup.*
import javax.swing.JLabel
import javax.swing.JPanel
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.editor.colors.EditorFontType

import com.github.latiosinaltomare.firstplugin.editorModify.MyEditorHelper
import com.intellij.openapi.editor.*
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import org.json.JSONObject
import java.awt.*

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

class MySimpleStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {

    override fun ID(): @NotNull String {
        return "MySimpleStatusBarWidget"
    }

    override fun getPresentation(type: StatusBarWidget.PlatformType): StatusBarWidget.WidgetPresentation {
        return this
    }

    override fun install(statusBar: StatusBar) {
        // 安装后的初始化操作
    }

    override fun dispose() {
        // 清理资源
    }

    override fun getText(): String {
        return "Javice"  // 状态栏上显示的文字
    }

    override fun getAlignment(): Float {
        return Component.CENTER_ALIGNMENT
    }

    override fun getTooltipText(): String {
        return "输入密钥"
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? {
        return Consumer { mouseEvent ->
            // 创建 JTextArea 并设置自动换行
            val textArea = JTextArea(10, 30)
            textArea.lineWrap = true
            textArea.wrapStyleWord = true
            textArea.border = BorderFactory.createEmptyBorder()
            textArea.background= Color.DARK_GRAY

            // 去掉横向滚动条，仅保留纵向滚动条
            val scrollPane = JScrollPane(textArea)
            scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            scrollPane.border = BorderFactory.createEmptyBorder()
            scrollPane.background=Color.DARK_GRAY

            // 创建一个 JLabel 用于显示额外的提示信息
            val label = JLabel("请输入您的密钥：")

            // 创建一个 JPanel 并将 JLabel 和 JScrollPane 添加到其中
            val panel = JPanel(BorderLayout())
            panel.add(label, BorderLayout.NORTH)
            panel.add(scrollPane, BorderLayout.CENTER)

            // 使用 JOptionPane 显示对话框，带有确认和取消按钮
            val option = JOptionPane.showConfirmDialog(
                null,
                panel,
                "输入密钥",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
            )

            if (option == JOptionPane.OK_OPTION) {
                val userInput = textArea.text
                if (userInput.isNotBlank()) {
                    // 获取存储服务并保存用户输入
                    // 获取 Project 级别的存储服务
                    val settings = MyPluginSettings.getInstance()
                    settings.myState.userInput = userInput // 存储用户输入

                    JOptionPane.showMessageDialog(null, "密钥已保存！", "成功", JOptionPane.INFORMATION_MESSAGE)
                } else {
                    JOptionPane.showMessageDialog(null, "输入内容不能为空！", "错误", JOptionPane.ERROR_MESSAGE)
                }
            }

        }
    }
}

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

