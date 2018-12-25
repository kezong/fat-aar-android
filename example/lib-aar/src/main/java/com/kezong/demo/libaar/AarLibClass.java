package com.kezong.demo.libaar;

import android.content.Context;

/**
 * @author kezong on 2018/12/25.
 */
public class AarLibClass {

    public static final String TAG = AarLibClass.class.getSimpleName();

    public String getLibName(Context ctx) {
        return ctx.getResources().getString(R.string.app_name_aar);
    }
}
