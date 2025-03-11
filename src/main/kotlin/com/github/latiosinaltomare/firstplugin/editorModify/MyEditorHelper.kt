package com.github.latiosinaltomare.firstplugin.editorModify

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import java.awt.*
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class MyEditorHelper(private val project: Project) {

    //获取当前活动的编辑器
    fun getCurrentEditor(project: Project): Editor?{
        val fileEditorManager=FileEditorManager.getInstance(project)
        val selectedEditor=fileEditorManager.selectedTextEditor
        return selectedEditor
    }

    //在编辑器中插入文本
    fun insertTextToEditor(editor: Editor, offset: Int, text: String){
        val document=editor.document
        WriteCommandAction.runWriteCommandAction(project){
            document.insertString(offset, text)//表示在末尾插入
        }
    }

    private var currentInlay: Inlay<*>?=null

    fun showInlayHint(editor: Editor, offset: Int, hintText: String){

        println("插入提示！")
        val inlayModel: InlayModel =editor.inlayModel
        //设置灰色的字体属性
        val textAttributes= TextAttributes()
        textAttributes.foregroundColor= Color.GRAY
        textAttributes.fontType= Font.ITALIC

        //在指定位置插入提示

        currentInlay=inlayModel.addInlineElement(offset, true, object:EditorCustomElementRenderer{
            override fun paint(
                inlay: Inlay<*>,
                g: Graphics,
                targetRegion: Rectangle, textAttributes:TextAttributes
                ){
                g.color=Color.GRAY
                g.font=g.font.deriveFont(Font.ITALIC)
                g.drawString(hintText, targetRegion.x, targetRegion.y+g.fontMetrics.ascent)
            }

            override fun calcWidthInPixels(inlay: Inlay<*>): Int {

                val metrics=inlay.editor.contentComponent.getFontMetrics(Font(editor.colorsScheme.editorFontName, Font.ITALIC,editor.colorsScheme.editorFontSize))
                return metrics.stringWidth(hintText)+50
            }
        })
    }
    
    //添加tab监听
    fun addEditorKeyListener(editor: Editor, offset:Int, suggestion: String){
        print("监听就绪！")
        editor.contentComponent.addKeyListener(
            object: KeyAdapter() {
                override fun keyPressed(e: KeyEvent){
                    if (e.keyCode==KeyEvent.VK_TAB){
                        println("Tab按下！")
                        insertTextToEditor(editor, offset, suggestion)
                    }
                }
            }
        )
    }

    fun addGlobalKeyDispatcher(editor: Editor, offset:Int, suggestion: String) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(object : KeyEventDispatcher {
            override fun dispatchKeyEvent(e: KeyEvent): Boolean {
                if (e.id == KeyEvent.KEY_PRESSED && e.keyCode == KeyEvent.VK_TAB) {
                    // 消耗事件，防止进一步处理 Tab 键
                    e.consume()

                    println("Tab按下！")
                    currentInlay?.dispose()
                    insertTextToEditor(editor, offset, suggestion)

                    return true
                }
                return false
            }
        })
    }

    fun addGlobalKeyDispatcherToOverrideTab(editor: Editor, offset:Int, suggestion: String) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(object : KeyEventDispatcher {
            override fun dispatchKeyEvent(e: KeyEvent): Boolean {
                if (e.id == KeyEvent.KEY_PRESSED && e.keyCode == KeyEvent.VK_TAB) {
                    println("Tab按下！")

                    currentInlay?.dispose()
                    insertTextToEditor(editor, offset, suggestion)

                    // 消耗事件，防止进一步处理 Tab 键
                    //e.consume()

                    // 返回 true，表示事件已经完全被处理，不会传递给其他组件
                    return false
                }
                return false
            }
        })
    }

    fun showBalloonPopupAtRight(editor: Editor, message: String, hint: String) {
        // 获取光标的位置
        val caretModel = editor.caretModel
        val logicalPosition = caretModel.logicalPosition
        val point = editor.logicalPositionToXY(logicalPosition)


        // 创建一个 JPanel 作为 Balloon 的内容
        val panel = JPanel(FlowLayout())

        // 创建 JLabel 显示提示文本
        val label = JLabel(message)
        label.font = Font("SansSerif", Font.PLAIN, 12) // 设置字体样式
        panel.add(label)

        // 在光标位置右侧显示气泡提示
        val balloonBuilder =com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
            .createBalloonBuilder(panel)
            .setFillColor(JBColor(0xFFFFFF, 0x000000))  // 背景颜色
            .setHideOnKeyOutside(true)
            .setHideOnClickOutside(true)

        balloonBuilder.setFadeoutTime(100000000000000000) // 设置提示显示时间
        val balloon = balloonBuilder.createBalloon()


        // 创建“接受代码”的按钮
        val acceptButton = JButton("Accept")
        acceptButton.addActionListener {
            // 用户点击“接受代码”时执行的逻辑
            println("用户接受了代码")
            // 这里可以插入代码到编辑器中
            insertTextToEditor(editor, currentInlay!!.offset, hint)
            currentInlay?.dispose()
            balloon.hide()
        }
        panel.add(acceptButton)

        // 创建“不接受代码”的按钮
        val rejectButton = JButton("Reject")
        rejectButton.addActionListener {
            // 用户点击“不接受代码”时执行的逻辑
            println("用户拒绝了代码")
            //删除
            currentInlay?.dispose()
            balloon.hide()
        }
        panel.add(rejectButton)

        // 使用相对位置来显示 Balloon，将气泡放在右侧
        if(currentInlay!=null) {
            val adjustedPoint = Point(
                point.x + currentInlay!!.widthInPixels,
                Math.round(point.y + 0.5 * (editor.lineHeight)).toInt()
            ) // 向右偏移 20 像素，避免遮挡代码
            val relativePoint = RelativePoint(editor.contentComponent, adjustedPoint)

            balloon.show(relativePoint, Balloon.Position.atRight) // 将箭头设置为向左，气泡在右侧显示
        }
    }

}