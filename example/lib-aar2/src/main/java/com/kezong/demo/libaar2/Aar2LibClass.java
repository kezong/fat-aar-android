package com.kezong.demo.libaar2;

import android.content.Context;

/**
 * @author kezong on 2018/12/25.
 */
public class Aar2LibClass {

    public static final String TAG = Aar2LibClass.class.getSimpleName();

    public String getLibName(Context ctx) {
        return ctx.getResources().getString(R.string.app_name_aar2);
    }
}
