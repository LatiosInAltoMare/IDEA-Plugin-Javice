package com.github.latiosinaltomare.firstplugin.toolWindow

import com.github.latiosinaltomare.firstplugin.widget.MyPluginSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import javax.swing.JButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import javax.swing.*
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.border.EmptyBorder
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import javax.swing.SwingUtilities
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager

import com.vladsch.flexmark.util.data.MutableDataSet
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension

import com.intellij.ui.jcef.JBCefApp
import okhttp3.RequestBody.Companion.toRequestBody

import java.io.IOException

import java.util.concurrent.CountDownLatch


class MyToolWindowFactory : ToolWindowFactory {

    init {
        if (!JBCefApp.isSupported()) {
            JOptionPane.showMessageDialog(
                null,
                "WebView is not supported in this environment",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        println("创建问答窗口")
        val myToolWindow = MyToolWindow()
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow {

        private var isGenerating = false  // 状态变量，是否正在生成回复
        private var currentProcess: Process? = null  // 用于保存生成回复的进程
        private var currentEditorMessagePanel: JEditorPane = JEditorPane() //保存当前对话框，用于流式输出
        //需要存储用户当前选择的模型
        private var currentModel: String?="DeepSeek-R1 Standard"
//        private var latch: CountDownLatch ?= null
        private var generateCall: Call? = null

        //需要存储对话记录
        val chatHistory=JSONArray()
        private var fullResponse = ""

        fun getContent() = JBPanel<JBPanel<*>>().apply {

            layout = BorderLayout()

            // 创建主聊天显示区域
            val chatPanel = JBPanel<JBPanel<*>>().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = EmptyBorder(10, 10, 10, 10)  // 添加边距
            }

            // 在 chatPanel 中添加垂直 glue，将消息推到顶部
            chatPanel.add(Box.createVerticalGlue())

            // 使用滚动窗格包裹聊天面板
            val scrollPane = JBScrollPane(chatPanel).apply {
                verticalScrollBar.unitIncrement = 16
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            }
            scrollPane.border = EmptyBorder(0, 0, 0, 0) // 去除滚动窗格的默认边框
            add(scrollPane, BorderLayout.CENTER)

            // **输入区域和 Send 按钮整体上移**
            val inputPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                border = EmptyBorder(5, 10, 5, 10) // 减少底部间距，让它上移
            }

            val (inputFieldPanel, inputField) = createRoundedTextField()

            val sendButton = JButton("Send").apply {
                font = Font("Dialog", Font.BOLD, 14)
                foreground = Color.WHITE
                isFocusPainted = false
            }

            inputPanel.add(inputFieldPanel, BorderLayout.CENTER)
            inputPanel.add(sendButton, BorderLayout.EAST)

// **新的清空按钮和下拉框区域**
            val clearPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                border = EmptyBorder(5, 10, 10, 10) // 让它贴近底部
                preferredSize = Dimension(250, 50) // 设置整个面板的大小
            }

// **创建下拉框**
            val optionsComboBox = JComboBox<String>().apply {
                addItem("DeepSeek-R1 Standard")
                addItem("DeepSeek-R1 70B")
                font = Font("Dialog", Font.PLAIN, 14)
                preferredSize = Dimension(120, 40) // 设置下拉框大小

                // 监听选择变化
                addActionListener {
                    currentModel = selectedItem as String
                    println("当前选中的值: $currentModel") // 你可以在这里绑定数据
                }
            }

// **清空按钮**
            val clearButton = JButton("Start New Chat").apply {
                font = Font("Dialog", Font.BOLD, 14)
                foreground = Color.WHITE
                isFocusPainted = false
                preferredSize = Dimension(120, 40) // 设置按钮大小
            }

// **创建水平面板，将下拉框和按钮放入**
            val clearPanelContent = JBPanel<JBPanel<*>>(GridLayout(1, 2)).apply {
                add(optionsComboBox) // 左侧：下拉框
                add(clearButton) // 右侧：清空按钮
            }

            clearPanel.add(clearPanelContent, BorderLayout.CENTER) // 将内容添加到 clearPanel 中

// **将输入框放在底部上方，Clear 按钮和下拉框放在最底部**
            val bottomPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(inputPanel)
                add(clearPanel)
            }

            add(bottomPanel, BorderLayout.SOUTH) // 整体放在南侧

            // **添加按钮点击事件**
            sendButton.addActionListener {
                if (isGenerating) {
                    stopGeneration(sendButton)
                } else {
                    sendMessage(chatPanel, scrollPane, inputField, sendButton)
                }
            }
            clearButton.addActionListener {
                clearChat(chatPanel)
            }

            // 添加键盘监听器，在按下 "Enter" 键时触发发送消息
            inputField.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER && !isGenerating) {
                        sendMessage(chatPanel, scrollPane, inputField, sendButton)
                    }
                }
            })
        }

        private fun sendMessage(chatPanel: JPanel, scrollPane: JBScrollPane, inputField: JBTextField, sendButton: JButton) {
            var userMessage = inputField.text.trim()
//            latch = CountDownLatch(1)
            val project = ProjectManager.getInstance().openProjects.firstOrNull()
            if (project != null) {
                val editor = FileEditorManager.getInstance(project).selectedTextEditor
                if (editor != null) {
                    val selectionModel = editor.selectionModel
                    // 后续操作
                    if (selectionModel.hasSelection()) {
                        val code=selectionModel.selectedText

                        userMessage = """
User selected the code below:
```
$code
```
$userMessage
"""

                        print(userMessage)
                    }
                }
            }

            if (userMessage.isNotEmpty()) {

                // 将用户的输入添加到聊天面板
                addMessageToChatPanel(chatPanel, "User", userMessage, isUser = true)
                inputField.text = ""

                userMessage="""
任何输出都要有思考过程，输出内容必须以 "<think>\n\n嗯" 开头。仔细揣摩用户意图，在思考过程之后，提供逻辑清晰且内容完整的回答，可以使用Markdown格式优化信息呈现。\n\n
{$userMessage}
""".trimIndent()

                // 改变按钮为 "Stop"
                sendButton.text = "Stop"
                isGenerating = true
                chatPanel.repaint()
                // 创建临时框，表示生成中
//                val temporaryPanel = addTemporaryMessage(chatPanel)
                addMessageToChatPanel(chatPanel, "Javice", "", isUser = false)
                // 将调用 Ollama 的代码移到后台线程，避免阻塞 UI 线程
                fullResponse = ""
                ApplicationManager.getApplication().executeOnPooledThread {
                    getModelResponse(userMessage, sendButton) { chunk ->
                        SwingUtilities.invokeLater {
                            // 移除临时消息框
//                            chatPanel.remove(temporaryPanel)
                            fullResponse += chunk.removePrefix("data: ")
                            //处理换行符
                            fullResponse = fullResponse.replace("[newline]", "\n\n")
                            fullResponse = fullResponse.replace("[h_newline]", "\n")
                            updateStreamMessagePanel(currentEditorMessagePanel, fullResponse)
                            // 保持滚动到最新的消息
                            scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.maximum
                        }
                    }
                }
            }
        }

        private fun stopGeneration(sendButton: JButton) {
            // 停止当前生成的进程

            val settings = MyPluginSettings.getInstance()
            val apiKey = settings.myState.userInput

            val request = Request.Builder().apply {
                url("http://172.18.36.55:5001/api/v1/stop") // 替换为你的服务器地址
                post(RequestBody.create(null, ByteArray(0)))
                header("Authorization", "Bearer $apiKey") // 添加用户 Key，与 buildRequest 一致
                header("Content-Type", "application/json")
                header("Accept", "application/json")
            }.build()

            val client = OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)  // 连接超时 60 秒
                .readTimeout(120, TimeUnit.SECONDS)    // 读取超时 120 秒
                .writeTimeout(120, TimeUnit.SECONDS)   // 写入超时 120 秒
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    println("Failed to send stop request: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    println("Stop request sent successfully")
                    generateCall?.cancel()
                    println("Request cancelled")
                }
            })

            currentProcess?.destroy()
            currentProcess = null
            isGenerating = false
            sendButton.text = "Send"
            println("The generation cancelled...")
        }

        // 方法：添加消息到聊天面板
        private fun addMessageToChatPanel(chatPanel: JPanel, sender: String, message: String, isUser: Boolean) {

            // 将 Markdown 转换为 HTML
            val markdown = message.trim()

            val htmlMessage = convertMarkdownToHtml(markdown)

            val messagePanel = RoundedPanel(if(isUser) Color(69,73,74) else Color(60, 63, 65), 30, 30).apply {
                layout = BorderLayout()

                // 使用 JEditorPane 显示消息文本
                val messageEditorPane = JEditorPane().apply {
                    contentType = "text/html"
                    text=htmlMessage
                    isEditable = false  // 不可编辑
                    isOpaque = false    // 背景透明

                    border = EmptyBorder(15, 15, 15, 15)  // 设置 JEditorPane 的内边距
                }

                add(messageEditorPane, BorderLayout.CENTER)

                maximumSize = Dimension(Int.MAX_VALUE,messageEditorPane.height+20)  // 最大宽度为聊天面板宽度的60%

                // 设置消息面板的对齐方式
                alignmentX = Component.RIGHT_ALIGNMENT

                currentEditorMessagePanel = messageEditorPane
            }

            // 在添加消息面板前，插入一个固定尺寸的空白区域

            chatPanel.add(Box.createRigidArea(Dimension(0, 10)))  // 空白区域，高度为10像素
            chatPanel.add(messagePanel, chatPanel.componentCount-1)
            chatPanel.revalidate()
            chatPanel.repaint()

        }

        private fun updateStreamMessagePanel(messageEditorPane: JEditorPane, message: String) {

//            val markdown = message.trim()
            val htmlMessage = convertMarkdownToHtml(message)

            messageEditorPane.apply {
                contentType = "text/html"
                text=htmlMessage
                isEditable = false  // 不可编辑
                isOpaque = false    // 背景透明
                border = EmptyBorder(15, 15, 15, 15)  // 设置 JEditorPane 的内边距
            }
        }

        private fun getModelResponse(prompt: String, sendButton: JButton,onNewChunk: (String) -> Unit) {
            val client = OkHttpClient.Builder()
                .connectTimeout(3000, TimeUnit.SECONDS)  // 合理缩短超时时间
                .readTimeout(1200, TimeUnit.SECONDS)
                .writeTimeout(1200, TimeUnit.SECONDS)
                .build()

            try {
                // 维护对话历史
                maintainChatHistory(prompt)

                // 构建请求
                val request = buildRequest(prompt)

                // 发送请求
//                client.newCall(request).execute().use { response ->
//                    return handleResponse(response);
//                }
                generateCall = client.newCall(request)
                generateCall!!.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        handleError(e, sendButton)
//                        latch?.countDown()
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.body?.let { body ->
                            body.byteStream().bufferedReader().useLines { lines ->
                                lines.forEach { line ->
                                    SwingUtilities.invokeLater {
//                                        val chunk = line.removePrefix("data: ").trim()
                                        onNewChunk(line)  // 每收到一行，就更新 UI
                                    }
                                }
                            }
                        }

                        chatHistory.put(JSONObject().apply {
                            put("role", "assistant")
                            put("content", fullResponse)
                        })

                        print(chatHistory)

                        resetUI(sendButton)  // 完成后恢复 UI
                    }
                })
            } catch (e: Exception) {
                handleError(e, sendButton)
//                return "Error: ${e.message}"
            } finally {
//                resetUI(sendButton)
            }
        }

        /* 辅助方法 */
        private fun maintainChatHistory(prompt: String) {
            chatHistory.put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })

            // 保持最多15轮对话
            val maxMessages = 15 * 2
            while (chatHistory.length() > maxMessages) {
                chatHistory.remove(0)
            }
        }

        private fun getModelEndpoint(): String {
            // 统一使用OpenAI标准API路径
            return "http://172.18.36.55:5001/api/v1/chat/completions"
        }

        private fun buildRequest(prompt: String): Request {
            val messages = mutableListOf<JSONObject>().apply {
                // 转换历史记录为OpenAI格式
                for (i in 0 until chatHistory.length()) {
                    add(chatHistory.getJSONObject(i))
                }
                // 添加当前消息
                add(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }

            val jsonBody = JSONObject().apply {
                put("model", currentModel?.lowercase()?.replace(" ", "-") ?:"") // 转换模型标识
                put("messages", JSONArray(messages))
                put("temperature", 0.7)
                put("stream", false)
            }

            val settings = MyPluginSettings.getInstance()
            val apiKey = settings.myState.userInput

            return Request.Builder().apply {
                url(getModelEndpoint())
                post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                header("Authorization", "Bearer $apiKey") // 强制要求认证头
                header("Content-Type", "application/json")
                header("Accept", "application/json")
            }.build()
        }

        private fun resetUI(sendButton: JButton) {
            SwingUtilities.invokeLater {
                sendButton.text = "Send"
                isGenerating = false
            }
        }

        private fun handleError(e: Exception, sendButton: JButton) {
            e.printStackTrace()
            SwingUtilities.invokeLater {
                sendButton.text = "Error"
            }
        }

        fun convertMarkdownToHtml(markdown: String): String {
            val options = MutableDataSet().apply {
                set(Parser.EXTENSIONS, listOf(
                    TablesExtension.create(),
                    TaskListExtension.create()
                ))
                set(HtmlRenderer.SOFT_BREAK, "<br/>")
                set(TablesExtension.CLASS_NAME, "md-table") // 为表格添加 CSS 类
            }

            val parser = Parser.builder(options).build()
            val renderer = HtmlRenderer.builder(options).build()
            return renderer.render(parser.parse(markdown))
        }

        private fun clearChat(chatPanel: JPanel) {
            // 移除所有聊天记录
            chatPanel.removeAll()

            // 重新添加顶部 glue 以保持消息从顶部开始显示
            chatPanel.add(Box.createVerticalGlue())

            // 清空聊天历史
            chatHistory.clear()

            // 重新绘制面板
            chatPanel.revalidate()
            chatPanel.repaint()

            println("聊天已清空")
        }

        fun createRoundedTextField(): Pair<JPanel, JBTextField> {
            val inputField = JBTextField().apply {
                font = Font("Dialog", Font.PLAIN, 14)
                background = Color(69,73,74) // 设置较浅的灰色
                isOpaque = false // 让背景透明，与 RoundedPanel 颜色融合
                border = EmptyBorder(5, 5, 5, 5)
                preferredSize = Dimension(180, 30) // 适当调整大小
            }

            val containerPanel = JPanel(BorderLayout()).apply {
                isOpaque = false // 让背景透明
                add(inputField, BorderLayout.CENTER)
            }

            val roundedPanel = RoundedPanel(Color(69, 73, 74), 20, 20).apply {
                preferredSize = Dimension(200, 40) // 设定适当大小
                layout = BorderLayout()
                add(containerPanel, BorderLayout.CENTER)
            }

            return Pair(roundedPanel, inputField) // 返回 JPanel 和 JBTextField
        }

        // 自定义 JPanel 以绘制圆角和背景色
        class RoundedPanel(private val bgColor: Color, private val arcWidth: Int, private val arcHeight: Int) : JPanel() {
            init {
                isOpaque = false  // 使 JPanel 背景透明，以手动绘制背景
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)  // 启用抗锯齿

                // 绘制带圆角的背景
                g2.color = bgColor
                g2.fillRoundRect(0, 0, width, height, arcWidth, arcHeight)

                // 确保其他组件的绘制不受影响
                super.paintComponent(g)
            }
        }
    }
}