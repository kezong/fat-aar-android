package com.kezong.demo.libaar;

import android.app.Activity;
import androidx.databinding.DataBindingUtil;

import android.os.Bundle;

import com.kezong.demo.libaar.databinding.DatabindingBinding;

public class TestActivity extends Activity {

    private DatabindingBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.databinding);
        User user = new User();
        user.setName("Hello World");
        user.setSex("[success][dataBinding] male");
        binding.setUser(user);
    }
}
