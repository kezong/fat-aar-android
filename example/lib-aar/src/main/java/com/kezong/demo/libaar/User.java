package com.kezong.demo.libaar;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

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
