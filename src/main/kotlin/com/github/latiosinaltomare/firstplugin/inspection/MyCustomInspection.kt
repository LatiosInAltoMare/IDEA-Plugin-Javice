package com.github.latiosinaltomare.firstplugin.inspection

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import javax.swing.*
import javax.swing.table.DefaultTableModel
import com.intellij.openapi.fileEditor.FileEditorManager
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.regex.Pattern
import javax.swing.plaf.basic.BasicSplitPaneDivider
import javax.swing.plaf.basic.BasicSplitPaneUI
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import javax.swing.SwingUtilities

class MyCustomInspection : ToolWindowFactory {

    private var bugAnalysisButton: JLabel? = null
    private var complianceCheckButton: JLabel? = null
    private var reanalyzeButton: JLabel? = null

    private var statusLabel: JLabel? = null // 用于显示当前是否正在生成回答
    private var currentProcess: Process? = null  // 用于保存生成回复的进程
    private var generatingBugDetection=false // 用于记录是否在生成BugDetection的内容
    private var generatingComplianceCheck=false // 用于记录是否在生成Compliance Check的内容

    private var cardLayout: CardLayout? = null
    private var cardPanel: JPanel? = null

    private var bugAnalysisPanel: JPanel? = null
    private var complianceCheckPanel: JPanel? = null
    private var currentPage = 0

    private var isBugAnalysisStopped = false
    private var isComplianceAnalysisStopped = false

    private var compliances: Array<String>?=null
    private var complianceDetails: Array<String>?=null

    private var bugs: Array<String>?=null
    private var bugDetails: Array<String>?=null

    private val loadingPanel = JPanel().apply {
        layout = BorderLayout()
        add(JLabel("加载中...", SwingConstants.CENTER), BorderLayout.CENTER)
    }

    private val defaultPanel = JPanel().apply {
        layout = BorderLayout()
        add(JLabel("请选择一个选项进行分析", SwingConstants.CENTER), BorderLayout.CENTER)
    }

    private val selectedBorder = BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 2, 0, Color.LIGHT_GRAY), // 底部边框
        BorderFactory.createEmptyBorder(7, 12, 7, 12) // 原始的内边距
    )
    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "Javice"
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(createToolWindowPanel(project), "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun createToolWindowPanel(project: Project): JPanel {
        val mainPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder()
        }

        // 顶部按钮栏：Bug 分析 和 规范性检查
        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = BorderFactory.createEmptyBorder()

            bugAnalysisButton = createLabelButton("Bug 分析") {
                currentPage=0
                isBugAnalysisStopped=false
                loadBugAnalysisData(project)
            }
            complianceCheckButton = createLabelButton("规范性检查") {
                currentPage = 1
                isComplianceAnalysisStopped=false
                loadComplianceCheckData(project)
            }
            reanalyzeButton = createLabelButton("重新分析") {
                reanalyzeData(project)
            }.apply {
                isVisible = false // 初始不可见
            }

            statusLabel = JLabel("Generating Response ").apply {
                isVisible = false // 初始不可见
                foreground = Color.LIGHT_GRAY
                font = font.deriveFont(Font.ITALIC) // 设置斜体
            }

            add(bugAnalysisButton)
            add(complianceCheckButton)
            add(reanalyzeButton)
            add(Box.createHorizontalGlue()) // 插入一个 Glue，将 statusLabel 推到最右边
            add(statusLabel) // 将 statusLabel 添加到最右边
        }

        mainPanel.add(buttonPanel, BorderLayout.NORTH)

        // 卡片布局，包含两个面板
        cardLayout = CardLayout()
        cardPanel = JPanel(cardLayout).apply {
            border = BorderFactory.createEmptyBorder()
            add(defaultPanel, "default")  // 默认面板
            add(loadingPanel, "loading")
        }
        mainPanel.add(cardPanel, BorderLayout.CENTER)

        cardLayout?.show(cardPanel, "default")
        return mainPanel
    }


    // 加载“Bug 分析”数据
    private fun loadBugAnalysisData(project: Project) {

        // 先显示加载面板
        cardLayout?.show(cardPanel, "loading")

        if (bugAnalysisPanel == null) {
            reanalyzeButton?.isVisible = true

            ApplicationManager.getApplication().executeOnPooledThread {
                cardLayout?.show(cardPanel, "bugAnalysis")

                if(!generatingBugDetection) {
                    generatingBugDetection = true
                    val (bugs_1, bugDetails_1) = bugDetection(project)
                    if(!isBugAnalysisStopped) {
                        bugs = bugs_1
                        bugDetails = bugDetails_1
                    }
                    generatingBugDetection = false
                }

                SwingUtilities.invokeLater {
                    if (bugs!=null && bugDetails!=null) { // 如果没有被手动停止，更新内容
                        bugAnalysisPanel = createPanelWithData("Bug 分析", bugs!!, bugDetails!!).apply {
                            border = BorderFactory.createEmptyBorder()
                        }
                        cardPanel?.add(bugAnalysisPanel, "bugAnalysis")
                        if (currentPage == 0) {
                            cardLayout?.show(cardPanel, "bugAnalysis")
                        }

                    }
                }
            }
        } else if(currentPage==0) {
            cardLayout?.show(cardPanel, "bugAnalysis")
        }
    }

    // 加载“规范性检查”数据
    private fun loadComplianceCheckData(project: Project) {
        cardLayout?.show(cardPanel, "loading")

        if (complianceCheckPanel == null) {

            reanalyzeButton?.isVisible = true
            ApplicationManager.getApplication().executeOnPooledThread {
                cardLayout?.show(cardPanel, "complianceCheck")

                if(!generatingComplianceCheck) {

                    generatingComplianceCheck = true
                    val (compliances_1, complianceDetails_1) = complianceInspection(project)
                    if(!isComplianceAnalysisStopped) {
                        compliances = compliances_1
                        complianceDetails = complianceDetails_1
                    }
                    generatingComplianceCheck = false
                }

                SwingUtilities.invokeLater {
                    if ((compliances!=null && complianceDetails!=null)) { // 如果没有被手动停止，更新内容
                        complianceCheckPanel = createPanelWithData("规范性检查", compliances!!, complianceDetails!!).apply {
                            border = BorderFactory.createEmptyBorder()
                        }
                        cardPanel?.add(complianceCheckPanel, "complianceCheck")
                        if(currentPage==1) {
                            cardLayout?.show(cardPanel, "complianceCheck")
                        }
                    }
                }
            }
        } else if(currentPage==1) {
            cardLayout?.show(cardPanel, "complianceCheck")
        }
    }

    // 重新分析数据
    private fun reanalyzeData(project: Project) {
        if(reanalyzeButton?.text=="重新分析") {
            if (currentPage == 0) {
                bugAnalysisPanel = null
                isBugAnalysisStopped = false // 重置标志位
                loadBugAnalysisData(project)
            } else {
                complianceCheckPanel = null
                isComplianceAnalysisStopped = false // 重置标志位
                loadComplianceCheckData(project)
            }
        }else{

            if(generatingComplianceCheck){
                generatingComplianceCheck=false
                isComplianceAnalysisStopped = true // 重置标志位
            }

            if(generatingBugDetection){
                generatingBugDetection=false
                isBugAnalysisStopped = true // 重置标志位
            }

            //毁掉进程
            currentProcess?.destroy()
            currentProcess = null
        }
    }

    private fun createPanelWithData(title: String, issues: Array<String>, details: Array<String>): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder()
        }
        val issuesTable = createIssuesTable(issues, title)

        val detailPane = JEditorPane("text/html", "选择问题以查看详细信息").apply {
            isEditable = false
            border = BorderFactory.createEmptyBorder(0, 10, 0, 10) // 上下左右各留出 10 像素的边距
        }

        issuesTable.selectionModel.addListSelectionListener {
            val selectedRow = issuesTable.selectedRow
            detailPane.text = if (selectedRow >= 0 && selectedRow < details.size) {
                "<html><body style='font-family:Dialog;font-size:12px'>${details[selectedRow]}</body></html>"
            } else {
                "<html><body style='font-family:Dialog;font-size:12px'>选择问题以查看详细信息</body></html>"
            }
        }

        val issuesScrollPane = JScrollPane(issuesTable).apply {
            border = BorderFactory.createEmptyBorder() // 去掉 JScrollPane 的边框
        }

        val detailScrollPane = JScrollPane(detailPane).apply {
            border = BorderFactory.createEmptyBorder() // 去掉 JScrollPane 的边框
        }

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, issuesScrollPane, detailScrollPane).apply {
            dividerLocation = 600
            resizeWeight = 0.3
            dividerSize = 5 // 设置分隔线为 1 像素
            border = BorderFactory.createEmptyBorder()
            setUI(object : BasicSplitPaneUI() {
                override fun createDefaultDivider(): BasicSplitPaneDivider {
                    return object : BasicSplitPaneDivider(this) {
                        init {
                            border = BorderFactory.createEmptyBorder()
                        }
                    }
                }
            })
        }

        panel.add(splitPane, BorderLayout.CENTER)
        return panel
    }

    private fun createIssuesTable(data: Array<String>, title: String): JBTable {
        val columnNames = arrayOf(title)
        val tableData = data.map { arrayOf(it) }.toTypedArray()
        val tableModel = object : DefaultTableModel(tableData, columnNames) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
        return JBTable(tableModel).apply {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            preferredScrollableViewportSize = Dimension(200, 100)
            border = BorderFactory.createEmptyBorder()

            // 去掉表格的网格线
            showHorizontalLines = false
            showVerticalLines = false
            intercellSpacing = Dimension(0, 0)
        }
    }

    private fun bugDetection(project: Project): Pair<Array<String>, Array<String>> {
        //提取编辑器中的内容

        val currentCode=getCurrentFileContent(project)
        print(currentCode)
        val prompt="""
            $currentCode
            Please check the code for bugs and output each bug in the following JSON format, do not add any additional comment including corrected code, only the JSON output is needed:

            {
              "Bug1": {
                "Brief Description": "A short summary of the bug, e.g., 'Variable not initialized'",
                "Detailed Description": "A detailed explanation of the bug’s cause, impact, etc., e.g., 'In function X, variable Y is not initialized, which causes an error during the call'"
              },
              "Bug2": {…}  // For additional bugs
            }
        """.trimIndent()
        // 调用Llama3模型
        return getLlama3Response(prompt)
    }

    private fun complianceInspection(project: Project): Pair<Array<String>, Array<String>> {
        // 提取编辑器中的内容
        val currentCode=getCurrentFileContent(project)
        print(currentCode)
        val prompt="""
            $currentCode
            Please review the code for adherence to best practices and coding standards. Identify any issues related to code readability, naming conventions, duplicate code, function complexity, and other coding norms. Provide each issue in the following JSON format; do not include any additional commentary or corrected code—only JSON output is required:
        
            {
              "Issue1": {
                "Brief Description": "A short summary of the issue, e.g., 'Inconsistent variable naming'",
                "Detailed Description": "A detailed explanation of the issue, including why it is considered problematic and its potential impact, e.g., 'Variable name 'x1' does not follow naming conventions, reducing code readability'",
              },
              "Issue2": {…}  // For additional issues
            }
        """.trimIndent()

        //调用Llama3模型
        return getLlama3Response(prompt)
    }


    private fun createLabelButton(text: String, onClick: () -> Unit): JLabel {
        return JLabel(text, SwingConstants.CENTER).apply {
            isOpaque = true
            background = Color.DARK_GRAY
            foreground = Color.WHITE
            border = BorderFactory.createEmptyBorder(7, 12, 7, 12)

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    onClick()
                    if(text=="Bug 分析"||text=="规范性检查")
                        setSelectedButton(this@apply) // 设置选中状态
                }

                override fun mouseEntered(e: MouseEvent?) {
                    background = Color.GRAY
                }

                override fun mouseExited(e: MouseEvent?) {
                    background = Color.DARK_GRAY
                }
            })
        }
    }

    // 设置选中状态的按钮
    private fun setSelectedButton(selectedButton: JLabel) {
        // 将所有按钮恢复默认边框
        bugAnalysisButton?.border = BorderFactory.createEmptyBorder(7, 12, 7, 12)
        complianceCheckButton?.border = BorderFactory.createEmptyBorder(7, 12, 7, 12)

        // 为选中按钮添加底部边框
        selectedButton.border = selectedBorder
    }

    // 获取当前文件的内容
    private fun getCurrentFileContent(project: Project): String? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        val document = editor.document
        return document.text  // 返回文档内容
    }

    // 方法：移除 ANSI 控制字符
    fun removeAnsiCodes(text: String): String {
        // 使用正则表达式移除 ANSI 控制字符
        val ansiPattern = Pattern.compile("\\u001B\\[\\?[0-9]+[a-zA-Z]")
        return ansiPattern.matcher(text).replaceAll("")
    }

    // 方法：移除ANSI 控制字符
    fun removeAnsiCodes_1(text: String): String {
        // 使用正则表达式移除 ANSI 控制字符
        val ansiPattern = Pattern.compile("\\u001B\\[[0-9;]*[a-zA-Z]")
        return ansiPattern.matcher(text).replaceAll("")
    }

    @Synchronized
    private fun getLlama3Response(prompt: String): Pair<Array<String>, Array<String>>{
        statusLabel?.isVisible = true // 显示“Generating Response”
        reanalyzeButton?.text="停止分析"
        //解析json格式的报告
        val response=StringBuilder()
        val client = OkHttpClient()

        try {
            // 构建 JSON 请求体
            val jsonBody = JSONObject()
            jsonBody.put("prompt", prompt)

            // 创建 HTTP 请求对象
            val requestBody = RequestBody.create(
                "application/json; charset=utf-8".toMediaType(),
                jsonBody.toString()
            )
            val request = Request.Builder()
                .url("http://172.18.36.55:5000/api/generate")  // 替换为实际的 API 地址
                .post(requestBody)
                .build()

            println("开始发送请求到服务器")

            // 发送同步请求
            val call = client.newCall(request)
            val httpResponse = call.execute()

            // 检查响应状态码
            if (httpResponse.isSuccessful) {
                val responseBody = httpResponse.body?.string()
                println("服务器返回: $responseBody")

                // 解析 JSON 响应
                val jsonResponse = JSONObject(responseBody ?: "{}")
                val serverResponse = jsonResponse.getString("response")

                response.append(serverResponse)
            } else {
                println("请求失败，状态码: ${httpResponse.code}")
                response.append("Error: ${httpResponse.message}")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            response.append("Error: ${e.message}")
        }

        println("生成的回复：${response.toString().trim()}")

        //解析生成的答复
        val llmResponse=response.toString()
        println(llmResponse)
        println(1)
        val jsonPattern = "(?s)\\{.*\\}".toRegex()
        val jsonMatch = jsonPattern.find(llmResponse)
        println(jsonMatch?.value)
        println(2)

        statusLabel?.isVisible = false  // 隐藏“Generating Response”
        reanalyzeButton?.text="重新分析"
        if (jsonMatch != null) {
            // Parse extracted JSON string
            val jsonString = jsonMatch.value
            try {
                val jsonObject = org.json.JSONObject(jsonString)  // 将提取的 JSON 字符串解析为 JSON 对象

                // Arrays to store brief descriptions and detailed descriptions
                val briefDescriptions = mutableListOf<String>()
                val detailedDescriptions = mutableListOf<String>()

                // Loop through each bug in JSON
                for (key in jsonObject.keys()) {
                    val bug = jsonObject.getJSONObject(key)
                    val briefDescription = bug.getString("Brief Description")
                    val detailedDescription = bug.getString("Detailed Description")  // 使用 getString 获取详细描述

                    // Add to arrays
                    briefDescriptions.add(briefDescription)
                    detailedDescriptions.add(detailedDescription)
                }

                // Convert lists to arrays
                val briefDescriptionsArray = briefDescriptions.toTypedArray()
                val detailedDescriptionsArray = detailedDescriptions.toTypedArray()
                return Pair(briefDescriptionsArray, detailedDescriptionsArray)
            }catch(e: Exception){
                print(e.stackTrace)
                return Pair(arrayOf("Generation Error, Please Try again"), arrayOf("Generation Error, Please Try again"))
            }
        } else {
            return Pair(arrayOf("No bug detected"), arrayOf("No bug detected"))
        }

    }
}