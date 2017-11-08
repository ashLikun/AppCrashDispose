package com.ashlikun.appcrash.simple;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.ashlikun.appcrash.config.CaocConfig;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        throw new RuntimeException("aaaaaaaaaa");
    }
}
