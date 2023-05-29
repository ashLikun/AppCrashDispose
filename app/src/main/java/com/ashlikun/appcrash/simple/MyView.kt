package com.ashlikun.appcrash.simple

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/**
 * 作者　　: 李坤
 * 创建时间: 2023/5/29　12:46
 * 邮箱　　：496546144@qq.com
 *
 * 功能介绍：
 */
class MyView @JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
//        throw RuntimeException("aaaaaaaaaa")
    }
}