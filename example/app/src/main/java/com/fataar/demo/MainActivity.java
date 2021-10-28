package com.fataar.demo;

import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.facebook.common.executors.CallerThreadExecutor;
import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.kezong.demo.javalib.JavaLib1;
import com.kezong.demo.lib.MainLibClass;
import com.kezong.demo.lib.KotlinInMain;
import com.kezong.demo.libaar.AarFlavor;
import com.kezong.demo.libaar.AarLibClass;
import com.kezong.demo.libaar.KotlinTest2;
import com.kezong.demo.libaar.LibCountries;
import com.kezong.demo.libaar.TestActivity;
import com.kezong.demo.libaar2.Aar2LibClass;
import com.kezong.demo.libaarlocal.AarLocalLibClass;
import com.kezong.demo.libaarlocal2.AarLocal2LibClass;
import com.kezong.demo.lib.R.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;

/**
 * @author kezong on 2020/12/11.
 */
public class MainActivity extends FragmentActivity {

    private ViewGroup mRoot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);
        mRoot = findViewById(R.id.root_layout);
        testActivity();
        testMainLibClassMerge();
        testClassMerge();
        testClassMerge2();
        testJarMerge();
        testResourceMerge();
        testResourceMerge2();
        testKotlinTopLevel();
        testKotlinTopLevel2();
        testLocalAar1();
        testLocalAar2();
        testSoMerge();
        testAssetsMerge();
        testRemoteAar();
        testFlavor();
        testEnum();
    }

    private void testEnum() {
        addTestView("enums", "lib flag ee", LibCountries.ESTONIA.getFlagRes() == R.drawable.lib_main_flag_ee);
    }

    private void testFlavor() {
        addTestView("flavor", AarFlavor.TAG, true);
    }

    private void testRemoteAar() {
        try {
            Fresco.initialize(this);
            ImageRequest imageRequest = ImageRequestBuilder
                    .newBuilderWithSource(Uri.parse("https://ss0.bdstatic.com/70cFvHSh_Q1YnxGkpoWK1HF6hhy/it/u=3096503764,1460949822&fm=26&gp=0.jpg"))
                    .setProgressiveRenderingEnabled(true)
                    .build();
            ImagePipeline imagePipeline = Fresco.getImagePipeline();
            DataSource<CloseableReference<CloseableImage>>
                    dataSource = imagePipeline.fetchDecodedImage(imageRequest, getApplicationContext());
            dataSource.subscribe(new BaseBitmapDataSubscriber() {

                @Override
                public void onNewResultImpl(Bitmap bitmap) {
                    mRoot.post(() -> {
                        ImageView imageView = new ImageView(MainActivity.this);
                        imageView.setImageBitmap(bitmap);
                        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(100, 100);
                        mRoot.addView(imageView, ll);
                        addTestView("remote aar merge", "yes", true);
                    });
                }

                @Override
                public void onFailureImpl(DataSource dataSource) {
                }
            }, CallerThreadExecutor.getInstance());
        } catch (Throwable e) {
            addTestView("remote aar merge", e.getMessage(), false);
        }
    }

    private void testSoMerge() {
        try {
            System.loadLibrary("gnustl_shared");
        } catch (Throwable e) {
            addTestView("so", e.getMessage(), false);
            return;
        }

        addTestView("so", "✔️", true);
    }

    private void testAssetsMerge() {
        Bitmap bitmap;
        AssetManager assetManager = this.getAssets();
        try {
            InputStream inputStream = assetManager.open("cat.jpg");
            bitmap = BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            addTestView("assets", e.getMessage(), false);
            return;
        }

        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mRoot.addView(imageView, ll);
        addTestView("assets", "look cat!", true);
    }

    private void testJarMerge() {
        String text = String.valueOf(JavaLib1.class.getSimpleName());
        addTestView("external jars", text, true);
    }


    private void testActivity() {
        Button button = new Button(this);
        button.setText("Activity Test");
        button.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, TestActivity.class);
            startActivity(intent);
        });
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mRoot.addView(button, ll);
    }

    public void testKotlinTopLevel() {
        String text = String.valueOf(KotlinInMain.test());
        addTestView("kotlin", text, TextUtils.equals(text, "120"));
    }

    public void testKotlinTopLevel2() {
        String text = String.valueOf(KotlinTest2.test2());
        addTestView("kotlin2", text, TextUtils.equals(text, "130"));
    }

    public void testResourceMerge() {

        for (Field field : R.string.class.getFields()) {
            Log.d("R class name:", field.getName());
        }

        String text = new AarLibClass().getLibName(this);
        addTestView("resource", text, TextUtils.equals(text, "lib-aar eng"));
    }

    public void testResourceMerge2() {
        String text = this.getResources().getString(R.string.lib_main_app_name_aar2);
        addTestView("resource2", text, TextUtils.equals(text, "lib-aar2"));
    }

    public void testClassMerge2() {
        String text = Aar2LibClass.TAG;
        addTestView("lib class2", text, TextUtils.equals(text, Aar2LibClass.class.getSimpleName()));
    }

    public void testMainLibClassMerge() {
        String text = String.valueOf(MainLibClass.test());
        addTestView("main class", text, TextUtils.equals(text, "200"));
    }

    public void testClassMerge() {
        String text = AarLibClass.TAG;
        addTestView("lib class", text, TextUtils.equals(text, AarLibClass.class.getSimpleName()));
    }

    public void addTestView(String title, String text, boolean success) {
        TextView textView = new TextView(this);
        String s;
        if (success) {
            s = "[Success] " + "[" + title + "] " + text;
        } else {
            s = "[Fail]" + "[" + title + "] " + text;
        }
        textView.setText(s);
        mRoot.addView(textView);
    }

    private void testLocalAar1() {
        try {
            String text = AarLocalLibClass.TAG;
            addTestView("local aar1", text, TextUtils.equals(text, AarLocalLibClass.class.getSimpleName()));
        } catch (Exception e) {
            addTestView("local aar1", "flavor 2???", false);
        }
    }

    private void testLocalAar2() {
        try {
            String text = AarLocal2LibClass.TAG;
            addTestView("local aar2", text, TextUtils.equals(text, AarLocal2LibClass.class.getSimpleName()));
        } catch (Exception e) {
            addTestView("local aar2", "release???", false);
        }
    }
}
