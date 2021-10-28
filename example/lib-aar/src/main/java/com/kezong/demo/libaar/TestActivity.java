package com.kezong.demo.libaar;

import android.app.Activity;
import androidx.databinding.DataBindingUtil;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kezong.demo.libaar.databinding.DatabindingBinding;

public class TestActivity extends Activity {

    private DatabindingBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.LibAarTheme_Main);
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.databinding);
        User user = new User();
        user.setName("Hello World");
        user.setSex("[success][dataBinding] male");
        binding.setUser(user);

        for (LibCountries country : LibCountries.values()) {
            binding.container.addView(getCountryView(country));
        }
    }

    private View getCountryView(LibCountries country) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        TextView textView = new TextView(this);
        textView.setText(country.getName(this));
        ImageView imageView = new ImageView(this);
        imageView.setImageResource(country.getFlagRes());

        layout.addView(textView);
        layout.addView(imageView);

        return layout;
    }
}
