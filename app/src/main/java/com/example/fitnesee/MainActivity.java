package com.example.fitnesee;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private NutritionDatabase nutritionDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化界面组件
        Button startButton = findViewById(R.id.startButton);
        Button profileButton = findViewById(R.id.profileButton);

        // 检查视图是否为 null
        if (startButton == null || profileButton == null) {
            Log.e(TAG, "One or more views are null: " +
                    "startButton=" + (startButton == null) +
                    ", profileButton=" + (profileButton == null));
            Toast.makeText(this, "界面初始化失败，请检查布局文件", Toast.LENGTH_LONG).show();
            return;
        }
        Log.d(TAG, "All views initialized successfully.");

        nutritionDb = new NutritionDatabase(this);

        // 设置“开始记录”按钮点击事件
        startButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MealEntryActivity.class);
            startActivity(intent);
        });

        // 设置“设置个人信息”按钮点击事件
        profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (nutritionDb != null) {
            nutritionDb.close();
            nutritionDb = null;
        }
    }
}