package com.kezong.demo.libaar;

import android.databinding.BaseObservable;
import android.databinding.Bindable;

/**
 * @author yangchao on 2020/8/7.
 */
public class User extends BaseObservable {
    private String name;
    private String sex;

    @Bindable

    public String getName() {
        return name;
    }

    @Bindable
    public String getSex() {
        return sex;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }
}
