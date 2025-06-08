package com.example.fitnesee;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.TreeMap;

public class LogActivity extends AppCompatActivity {

    private static final String TAG = "LogActivity";
    private NutritionDatabase db;
    private ExpandableListView expandableLogList;
    private com.google.android.material.button.MaterialButton backToMealInputButton;
    private SimpleDateFormat dateFormat; // 修改为 MM月dd日 格式
    private SimpleDateFormat timestampFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        // 初始化时间格式
        dateFormat = new SimpleDateFormat("MM月dd日", Locale.getDefault()); // 只显示月日
        dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        timestampFormat = NutritionDatabase.timestampFormat; // 复用 NutritionDatabase 中的 MM月dd日 HH:mm:ss

        // 初始化数据库
        try {
            db = new NutritionDatabase(this);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize NutritionDatabase: " + e.getMessage(), e);
            Toast.makeText(this, "数据库初始化失败", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 获取界面组件
        expandableLogList = findViewById(R.id.expandableLogList);
        backToMealInputButton = findViewById(R.id.backToMealInputButton);

        // 检查视图是否为 null
        if (expandableLogList == null || backToMealInputButton == null) {
            Log.e(TAG, "One or more views are null: " +
                    "expandableLogList=" + (expandableLogList == null) +
                    ", backToMealInputButton=" + (backToMealInputButton == null));
            Toast.makeText(this, "界面初始化失败，请检查布局文件", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Log.d(TAG, "All views initialized successfully.");

        // 获取并组织日志数据
        List<NutritionDatabase.LogEntry> logs = null;
        try {
            logs = db.getUploadLogs();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get upload logs: " + e.getMessage(), e);
            Toast.makeText(this, "日志数据加载失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (logs == null) {
            Log.e(TAG, "getUploadLogs returned null");
            Toast.makeText(this, "日志数据加载失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 使用 TreeMap 按日期排序
        TreeMap<String, List<NutritionDatabase.LogEntry>> logsByDate = new TreeMap<>();
        for (NutritionDatabase.LogEntry log : logs) {
            try {
                if (log.timestamp == null) {
                    Log.w(TAG, "LogEntry timestamp is null for food: " + log.foodName);
                    continue; // 跳过时间戳为 null 的记录
                }
                String date = dateFormat.format(log.timestamp); // 格式化为 MM月dd日
                logsByDate.computeIfAbsent(date, k -> new ArrayList<>()).add(log);
            } catch (Exception e) {
                Log.e(TAG, "Failed to format timestamp for log entry: " + log.foodName + ", error: " + e.getMessage(), e);
            }
        }

        // 准备 ExpandableListView 数据
        ArrayList<String> groupList = new ArrayList<>(logsByDate.keySet());
        HashMap<String, ArrayList<String>> childMap = new HashMap<>();
        for (String date : groupList) {
            ArrayList<String> childList = new ArrayList<>();
            List<NutritionDatabase.LogEntry> entries = logsByDate.get(date);
            if (entries != null && !entries.isEmpty()) {
                for (NutritionDatabase.LogEntry log : entries) {
                    String formattedTimestamp = timestampFormat.format(log.timestamp); // MM月dd日 HH:mm:ss
                    childList.add("时间: " + formattedTimestamp + "\n" +
                            "食物: " + log.foodName + "\n" +
                            "克数: " + log.grams + " 克\n" +
                            "餐类型: " + (log.mealType != null ? log.mealType : "未知"));
                }
            } else {
                childList.add("无数据");
            }
            childMap.put(date, childList);
        }

        if (groupList.isEmpty()) {
            groupList.add("无日志记录");
            childMap.put("无日志记录", new ArrayList<String>() {{ add("暂无日志数据"); }});
        }

        // 设置 ExpandableListView 适配器
        try {
            ExpandableListAdapter adapter = new ExpandableListAdapter(this, groupList, childMap);
            expandableLogList.setAdapter(adapter);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set ExpandableListAdapter: " + e.getMessage(), e);
            Toast.makeText(this, "日志显示失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 设置返回按钮点击事件
        backToMealInputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LogActivity.this, MealEntryActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (db != null) {
            try {
                db.close();
            } catch (Exception e) {
                Log.e(TAG, "Failed to close database: " + e.getMessage(), e);
            }
            db = null;
        }
    }

    // 自定义 ExpandableListAdapter
    private static class ExpandableListAdapter extends BaseExpandableListAdapter {
        private final android.content.Context context;
        private final ArrayList<String> groupList;
        private final HashMap<String, ArrayList<String>> childMap;

        public ExpandableListAdapter(android.content.Context context, ArrayList<String> groupList, HashMap<String, ArrayList<String>> childMap) {
            this.context = context;
            this.groupList = groupList;
            this.childMap = childMap;
        }

        @Override
        public int getGroupCount() {
            return groupList.size();
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return childMap.get(groupList.get(groupPosition)).size();
        }

        @Override
        public Object getGroup(int groupPosition) {
            return groupList.get(groupPosition);
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return childMap.get(groupList.get(groupPosition)).get(childPosition);
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            String groupTitle = (String) getGroup(groupPosition);
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(android.R.layout.simple_expandable_list_item_1, parent, false);
            }
            TextView textView = convertView.findViewById(android.R.id.text1);
            if (textView != null) {
                textView.setText(groupTitle);
            }
            return convertView;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            String childText = (String) getChild(groupPosition, childPosition);
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent, false);
            }
            TextView textView = convertView.findViewById(android.R.id.text1);
            if (textView != null) {
                textView.setText(childText);
            }
            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }
    }
}