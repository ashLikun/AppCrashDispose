package com.ashlikun.appcrash.simple

import android.content.Intent
import android.os.Bundle
import com.ashlikun.appcrash.simple.R
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import java.lang.RuntimeException

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //        new Handler().postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                throw new RuntimeException("aaaaaaaaaa");
//            }
//        }, 3000);
//        findViewById<View>(R.id.button).setOnClickListener { v: View? -> throw RuntimeException("aaaaaaaaaa") }
        findViewById<View>(R.id.button).setOnClickListener { v: View? ->
            startActivity(Intent(this, Main2Activity::class.java))
        }
    }
}