package com.kezong.demo.libaarincluded;

import android.content.Context;

public class AarIncludedLibClass {

    public static final String TAG = AarIncludedLibClass.class.getSimpleName();

    public static String getLibName(Context ctx) {
        return ctx.getResources().getString(R.string.app_name_aar);
    }
}
