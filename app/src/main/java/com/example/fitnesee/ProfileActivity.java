package com.example.fitnesee;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";
    private EditText editTextWeight, editTextHeight, editTextAge;
    private Spinner spinnerGender, spinnerGoal;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // 初始化界面组件
        editTextWeight = findViewById(R.id.editTextWeight);
        editTextHeight = findViewById(R.id.editTextHeight);
        editTextAge = findViewById(R.id.editTextAge);
        spinnerGender = findViewById(R.id.spinnerGender);
        spinnerGoal = findViewById(R.id.spinnerGoal);
        Button submitButton = findViewById(R.id.submitButton);

        // 添加日志检查
        if (editTextWeight == null || editTextHeight == null || editTextAge == null ||
                spinnerGender == null || spinnerGoal == null || submitButton == null) {
            Log.e(TAG, "One or more views are null: " +
                    "weight=" + (editTextWeight == null) +
                    ", height=" + (editTextHeight == null) +
                    ", age=" + (editTextAge == null) +
                    ", gender=" + (spinnerGender == null) +
                    ", goal=" + (spinnerGoal == null) +
                    ", submit=" + (submitButton == null));
            Toast.makeText(this, "界面初始化失败，请检查布局文件", Toast.LENGTH_LONG).show();
            return;
        }
        Log.d(TAG, "All views initialized successfully.");

        // 初始化 SharedPreferences
        sharedPreferences = getSharedPreferences("UserProfile", MODE_PRIVATE);

        // 加载已保存的个人信息
        loadProfileData();

        // 设置性别下拉菜单
        ArrayAdapter<CharSequence> genderAdapter = ArrayAdapter.createFromResource(this,
                R.array.gender_options, android.R.layout.simple_spinner_item);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(genderAdapter);

        // 设置目标下拉菜单
        ArrayAdapter<CharSequence> goalAdapter = ArrayAdapter.createFromResource(this,
                R.array.goal_options, android.R.layout.simple_spinner_item);
        goalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGoal.setAdapter(goalAdapter);

        // 提交按钮点击事件
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveProfileData();
                Toast.makeText(ProfileActivity.this, "个人信息已保存", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(ProfileActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }

    private void loadProfileData() {
        editTextWeight.setText(sharedPreferences.getFloat("weight", 0f) > 0 ? String.valueOf(sharedPreferences.getFloat("weight", 0f)) : "");
        editTextHeight.setText(sharedPreferences.getFloat("height", 0f) > 0 ? String.valueOf(sharedPreferences.getFloat("height", 0f)) : "");
        editTextAge.setText(sharedPreferences.getInt("age", 0) > 0 ? String.valueOf(sharedPreferences.getInt("age", 0)) : "");
        spinnerGender.setSelection(sharedPreferences.getInt("genderIndex", 0));
        spinnerGoal.setSelection(sharedPreferences.getInt("goalIndex", 0));
    }

    private void saveProfileData() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        try {
            float weight = Float.parseFloat(editTextWeight.getText().toString().trim());
            float height = Float.parseFloat(editTextHeight.getText().toString().trim());
            int age = Integer.parseInt(editTextAge.getText().toString().trim());

            if (weight <= 0 || weight > 500) {
                Toast.makeText(this, "体重应在 0-500 kg 之间", Toast.LENGTH_SHORT).show();
                return;
            }
            if (height <= 0 || height > 250) {
                Toast.makeText(this, "身高应在 0-250 cm 之间", Toast.LENGTH_SHORT).show();
                return;
            }
            if (age <= 0 || age > 120) {
                Toast.makeText(this, "年龄应在 0-120 之间", Toast.LENGTH_SHORT).show();
                return;
            }

            editor.putFloat("weight", weight);
            editor.putFloat("height", height);
            editor.putInt("age", age);
            editor.putInt("genderIndex", spinnerGender.getSelectedItemPosition());
            editor.putInt("goalIndex", spinnerGoal.getSelectedItemPosition());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
            return;
        }
        editor.apply();
    }
}