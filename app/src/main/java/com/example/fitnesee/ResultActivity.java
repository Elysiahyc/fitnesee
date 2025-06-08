package com.example.fitnesee;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class ResultActivity extends AppCompatActivity {
    private static final String TAG = "ResultActivity";
    private TextView resultText; // 显示结果的文本视图
    private NutritionDatabase nutritionDb; // 营养数据库实例
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MM月dd日", Locale.getDefault()); // 用于分组
    private SimpleDateFormat timestampFormat = new SimpleDateFormat("MM月dd日 HH:mm:ss", Locale.getDefault()); // 用于显示

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result); // 设置布局文件

        // 设置时间区为北京时间 (CST)
        dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        timestampFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

        // 初始化界面组件
        resultText = findViewById(R.id.resultText);

        // 检查界面组件是否正确初始化
        if (resultText == null) {
            Log.e(TAG, "resultText is null, layout initialization failed");
            resultText = new TextView(this); // 应急处理
            resultText.setText("界面初始化失败，请检查布局文件");
            setContentView(resultText);
            return;
        }
        resultText.setMovementMethod(new ScrollingMovementMethod()); // 启用滚动

        try {
            nutritionDb = new NutritionDatabase(this); // 初始化数据库
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize NutritionDatabase: " + e.getMessage(), e);
            resultText.setText("数据库初始化失败: " + e.getMessage());
            return;
        }

        // 获取 Intent 数据并处理不同类型的结果
        Intent intent = getIntent();
        if (intent == null) {
            Log.w(TAG, "Intent is null");
            resultText.setText("未接收到有效数据");
            return;
        }

        String resultType = intent.getStringExtra("RESULT_TYPE");
        if (resultType == null) {
            Log.w(TAG, "Result type not specified in intent");
            resultText.setText("未指定结果类型");
            return;
        }

        switch (resultType) {
            case "DAILY_DATA":
                // 显示每日摄入数据
                String dailyResult = intent.getStringExtra("DAILY_RESULT");
                resultText.setText(dailyResult != null ? dailyResult : "未能获取每日数据");
                break;
            case "ERROR":
                // 显示错误信息
                String errorMessage = intent.getStringExtra("ERROR_MESSAGE");
                resultText.setText(errorMessage != null ? errorMessage : "未知错误");
                break;
            case "VIEW_LOGS":
                // 显示历史日志
                List<NutritionDatabase.LogEntry> logs = nutritionDb.getUploadLogs();
                if (logs == null || logs.isEmpty()) {
                    Log.d(TAG, "No logs found in database");
                    resultText.setText("暂无历史记录\n请添加新日志后查看");
                    return;
                }

                // 按日期分组日志
                Map<String, List<NutritionDatabase.LogEntry>> logsByDate = new HashMap<>();
                for (NutritionDatabase.LogEntry log : logs) {
                    try {
                        if (log.timestamp == null) {
                            Log.w(TAG, "Timestamp is null for log entry: " + log.foodName);
                            continue;
                        }
                        String date = dateFormat.format(log.timestamp); // 格式化为 "MM月dd日"
                        logsByDate.computeIfAbsent(date, k -> new ArrayList<>()).add(log);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to format timestamp for log entry: " + log.foodName + ", error: " + e.getMessage(), e);
                        resultText.setText("日志时间戳解析失败: " + e.getMessage());
                        return;
                    }
                }

                // 构建并显示日志内容
                StringBuilder logText = new StringBuilder();
                for (Map.Entry<String, List<NutritionDatabase.LogEntry>> entry : logsByDate.entrySet()) {
                    logText.append("=== ").append(entry.getKey()).append(" ===\n"); // 添加分隔线
                    for (NutritionDatabase.LogEntry log : entry.getValue()) {
                        String formattedTimestamp = timestampFormat.format(log.timestamp); // 格式化时间戳
                        logText.append("  时间：").append(formattedTimestamp).append("\n");
                        logText.append("  餐次：").append(log.mealType != null ? log.mealType : "未知").append("\n");
                        logText.append("  食物：").append(log.foodName).append(", ").append(String.format("%.1f", log.grams)).append("克\n");
                        logText.append("\n"); // 添加换行分隔
                    }
                    logText.append("\n"); // 分隔不同日期
                }
                resultText.setText(logText.toString());
                break;
            default:
                Log.w(TAG, "Invalid result type: " + resultType);
                resultText.setText("无效的结果类型");
        }

        // 获取返回按钮并设置点击事件
        Button backToMealInputButton = findViewById(R.id.backToMealInputButton);
        if (backToMealInputButton != null) {
            backToMealInputButton.setOnClickListener(v -> {
                Intent returnIntent = new Intent(ResultActivity.this, MealEntryActivity.class); // 使用新变量名
                startActivity(returnIntent);
                finish(); // 关闭当前页面
            });
        } else {
            Log.w(TAG, "backToMealInputButton is null, adding emergency button");
            backToMealInputButton = new Button(this);
            backToMealInputButton.setText("返回");
            backToMealInputButton.setOnClickListener(v -> {
                Intent returnIntent = new Intent(ResultActivity.this, MealEntryActivity.class); // 使用新变量名
                startActivity(returnIntent);
                finish();
            });
            setContentView(backToMealInputButton); // 应急处理
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (nutritionDb != null) {
            try {
                nutritionDb.close(); // 关闭数据库连接
            } catch (Exception e) {
                Log.e(TAG, "Failed to close database: " + e.getMessage(), e);
            }
        }
    }
}