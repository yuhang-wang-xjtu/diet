package cn.qingheng.app

import android.app.Activity
import android.os.Bundle
import android.view.WindowInsets
import android.widget.*

class HealthPrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding((24 * dp).toInt(), (40 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt()) }
        c.addView(TextView(this).apply { text = "健康数据使用说明"; textSize = 24f })
        c.addView(TextView(this).apply {
            textSize = 16f
            text = "\n轻衡可在授权后通过健康连接读取选定日期的步数，默认系统去重汇总，也可选择单一数据源；连接诊断读取近期记录的来源包名，不把多来源记录相加。\n\n没有健康连接时，可主动开启手机本机计步。需要身体活动权限，使用手机传感器及前台计步服务。启用后在后台记录，不读取小米或手环私有数据，也无法补回启用前历史。可在设置中停止服务、撤销身体活动权限。\n\n步数、日期、来源与更新时间均保存在本机，CSV导出包括本机采集存档。应用不上传健康数据。新增联网权限仅用于用户主动检查GitHub更新和下载APK，GitHub及其下载服务会接收常规网络请求信息，不包含饮食、体重或步数。\n\n关闭健康连接自动读取或撤销其权限后，缓存仍保留。本机计步也需单独停止；清除本机计步来源的当日记录会删除当日采集存档，若服务开启会继续记录后续步数。卸载会删除所有本机数据。\n\n步数不能直接确定强度和热量。自动TDEE模式不再次叠加运动；手动模式可按天选择是否把步行估算计入缺口。"
        })
        c.addView(Button(this).apply { text = "关闭"; setOnClickListener { finish() } })
        val scroll = ScrollView(this).apply { addView(c) }
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) { val bars = insets.getInsets(WindowInsets.Type.systemBars()); v.setPadding(bars.left, bars.top, bars.right, bars.bottom) }
            else { @Suppress("DEPRECATION") v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom) }
            insets
        }
        setContentView(scroll); scroll.requestApplyInsets()
    }
}
