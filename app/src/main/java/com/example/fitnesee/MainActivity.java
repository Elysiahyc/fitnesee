package com.example.fitnesee;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private EditText editTextWeight, editTextHeight, editTextAge;
    private Spinner spinnerGoal;
    private TextView resultText;
    private RecyclerView recyclerViewBreakfast, recyclerViewLunch, recyclerViewDinner;
    private MealAdapter breakfastAdapter, lunchAdapter, dinnerAdapter;
    private Button addBreakfastButton, addLunchButton, addDinnerButton, submitButton, viewLogsButton;
    private NutritionDatabase nutritionDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editTextWeight = findViewById(R.id.editTextWeight);
        editTextHeight = findViewById(R.id.editTextHeight);
        editTextAge = findViewById(R.id.editTextAge);
        spinnerGoal = findViewById(R.id.spinnerGoal);
        resultText = findViewById(R.id.resultText);
        recyclerViewBreakfast = findViewById(R.id.recyclerViewBreakfast);
        recyclerViewLunch = findViewById(R.id.recyclerViewLunch);
        recyclerViewDinner = findViewById(R.id.recyclerViewDinner);
        addBreakfastButton = findViewById(R.id.addBreakfastButton);
        addLunchButton = findViewById(R.id.addLunchButton);
        addDinnerButton = findViewById(R.id.addDinnerButton);
        submitButton = findViewById(R.id.submitButton);
        viewLogsButton = findViewById(R.id.viewLogsButton);

        if (editTextWeight == null || editTextHeight == null || editTextAge == null ||
                spinnerGoal == null || resultText == null || recyclerViewBreakfast == null ||
                recyclerViewLunch == null || recyclerViewDinner == null || addBreakfastButton == null ||
                addLunchButton == null || addDinnerButton == null || submitButton == null || viewLogsButton == null) {
            Toast.makeText(this, "界面初始化失败，请检查布局文件", Toast.LENGTH_LONG).show();
            return;
        }

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.goal_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGoal.setAdapter(adapter);

        nutritionDb = new NutritionDatabase(this);

        loadUserProfile();

        breakfastAdapter = new MealAdapter("breakfast");
        lunchAdapter = new MealAdapter("lunch");
        dinnerAdapter = new MealAdapter("dinner");

        recyclerViewBreakfast.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewLunch.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewDinner.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewBreakfast.setAdapter(breakfastAdapter);
        recyclerViewLunch.setAdapter(lunchAdapter);
        recyclerViewDinner.setAdapter(dinnerAdapter);

        addBreakfastButton.setOnClickListener(v -> breakfastAdapter.addFood());
        addLunchButton.setOnClickListener(v -> lunchAdapter.addFood());
        addDinnerButton.setOnClickListener(v -> dinnerAdapter.addFood());

        submitButton.setOnClickListener(v -> {
            String weightStr = editTextWeight.getText().toString().trim();
            String heightStr = editTextHeight.getText().toString().trim();
            String ageStr = editTextAge.getText().toString().trim();
            String goal = spinnerGoal.getSelectedItem().toString();

            if (weightStr.isEmpty() || heightStr.isEmpty() || ageStr.isEmpty() || goal.isEmpty()) {
                Toast.makeText(this, "请填写所有用户资料并选择目标", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                double weight = Double.parseDouble(weightStr);
                double height = Double.parseDouble(heightStr);
                int age = Integer.parseInt(ageStr);

                if (weight <= 0 || height <= 0 || age <= 0) {
                    Toast.makeText(this, "请输入有效数值（大于0）", Toast.LENGTH_SHORT).show();
                    return;
                }

                String gender = "male";
                String goalValue;
                switch (goal) {
                    case "保持":
                        goalValue = "maintain";
                        break;
                    case "减脂":
                        goalValue = "lose";
                        break;
                    case "增肌":
                        goalValue = "gain";
                        break;
                    default:
                        goalValue = "maintain";
                }

                nutritionDb.insertUserProfile(weight, height, age, gender, goalValue);

                List<NutritionDatabase.MealEntry> meals = new ArrayList<>();
                meals.addAll(breakfastAdapter.getMeals());
                meals.addAll(lunchAdapter.getMeals());
                meals.addAll(dinnerAdapter.getMeals());

                if (meals.isEmpty()) {
                    Toast.makeText(this, "请至少输入一种食物", Toast.LENGTH_SHORT).show();
                    return;
                }

                nutritionDb.fetchDailyFoodData(meals, new NutritionDatabase.OnDailyDataFetchedListener() {
                    @Override
                    public void onDataFetched(NutritionDatabase.DailyFoodData dailyFoodData, double totalCalories, double totalProtein, double totalFat, double totalCarb, double recommendedCalories, String advice) {
                        StringBuilder result = new StringBuilder();
                        result.append("每日摄入总热量：").append(String.format("%.1f", totalCalories)).append(" 千卡\n");
                        result.append("蛋白质：").append(String.format("%.1f", totalProtein)).append(" 克\n");
                        result.append("脂肪：").append(String.format("%.1f", totalFat)).append(" 克\n");
                        result.append("碳水化合物：").append(String.format("%.1f", totalCarb)).append(" 克\n");
                        result.append("推荐热量：").append(String.format("%.1f", recommendedCalories)).append(" 千卡\n\n");
                        result.append("早餐热量：").append(String.format("%.1f", dailyFoodData.breakfastCalories)).append(" 千卡\n");
                        result.append("午餐热量：").append(String.format("%.1f", dailyFoodData.lunchCalories)).append(" 千卡\n");
                        result.append("晚餐热量：").append(String.format("%.1f", dailyFoodData.dinnerCalories)).append(" 千卡\n\n");
                        result.append(advice);
                        resultText.setText(result.toString());
                    }

                    @Override
                    public void onError(String errorMessage) {
                        resultText.setText("错误: " + errorMessage);
                    }
                });
            } catch (NumberFormatException e) {
                Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
            }
        });

        viewLogsButton.setOnClickListener(v -> {
            List<NutritionDatabase.LogEntry> logs = nutritionDb.getUploadLogs();
            if (logs.isEmpty()) {
                resultText.setText("暂无历史记录");
                return;
            }

            Map<String, List<NutritionDatabase.LogEntry>> logsByDate = new HashMap<>();
            for (NutritionDatabase.LogEntry log : logs) {
                String date = log.timestamp.split(" ")[0];
                logsByDate.computeIfAbsent(date, k -> new ArrayList<>()).add(log);
            }

            StringBuilder logText = new StringBuilder();
            for (Map.Entry<String, List<NutritionDatabase.LogEntry>> entry : logsByDate.entrySet()) {
                logText.append("日期：").append(entry.getKey()).append("\n");
                for (NutritionDatabase.LogEntry log : entry.getValue()) {
                    logText.append(String.format("时间：%s\n餐次：%s\n食物：%s，%.1f克\n\n",
                            log.timestamp, log.mealType, log.foodName, log.grams));
                }
            }
            resultText.setText(logText.toString());
        });
    }

    private void loadUserProfile() {
        NutritionDatabase.UserProfile profile = nutritionDb.getUserProfile();
        if (profile != null) {
            editTextWeight.setText(String.valueOf(profile.weight));
            editTextHeight.setText(String.valueOf(profile.height));
            editTextAge.setText(String.valueOf(profile.age));
            String goal = profile.goal;
            int goalPosition;
            switch (goal) {
                case "maintain":
                    goalPosition = 0;
                    break;
                case "lose":
                    goalPosition = 1;
                    break;
                case "gain":
                    goalPosition = 2;
                    break;
                default:
                    goalPosition = 0;
            }
            spinnerGoal.setSelection(goalPosition);
        }
    }

    private class MealAdapter extends RecyclerView.Adapter<MealAdapter.ViewHolder> {
        private final List<NutritionDatabase.MealEntry> meals = new ArrayList<>();
        private final String mealType;

        MealAdapter(String mealType) {
            this.mealType = mealType;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            EditText editTextFoodName, editTextGrams;
            TextView textViewLabel;

            ViewHolder(View itemView) {
                super(itemView);
                editTextFoodName = itemView.findViewById(R.id.editTextFoodName);
                editTextGrams = itemView.findViewById(R.id.editTextGrams);
                textViewLabel = itemView.findViewById(R.id.textViewLabel);

                editTextFoodName.setSingleLine(true);
                editTextFoodName.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);

                editTextFoodName.addTextChangedListener(new SimpleTextWatcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            meals.get(position).foodName = s.toString();
                            Log.d(TAG, "Food name updated at position " + position + ": " + s.toString());
                        }
                    }
                });

                editTextGrams.addTextChangedListener(new SimpleTextWatcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            try {
                                double grams = s.toString().isEmpty() ? 0 : Double.parseDouble(s.toString());
                                if (grams < 0) {
                                    s.replace(0, s.length(), "0");
                                    Toast.makeText(MainActivity.this, "克数不能为负数", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                meals.get(position).grams = grams;
                            } catch (NumberFormatException e) {
                                Toast.makeText(MainActivity.this, "请输入有效的克数", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
            }
        }

        abstract class SimpleTextWatcher implements TextWatcher {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_meal_entry, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.textViewLabel.setText("食物 " + (position + 1));
            NutritionDatabase.MealEntry meal = meals.get(position);
            holder.editTextFoodName.setText(meal.foodName);
            holder.editTextGrams.setText(meal.grams > 0 ? String.valueOf(meal.grams) : "");
        }

        @Override
        public int getItemCount() {
            return meals.size();
        }

        public void addFood() {
            meals.add(new NutritionDatabase.MealEntry("", 0.0, mealType));
            notifyItemInserted(meals.size() - 1);
        }

        public List<NutritionDatabase.MealEntry> getMeals() {
            List<NutritionDatabase.MealEntry> validMeals = new ArrayList<>();
            for (NutritionDatabase.MealEntry meal : meals) {
                if (!meal.foodName.trim().isEmpty() && meal.grams > 0) {
                    meal.mealType = mealType;
                    validMeals.add(meal);
                }
            }
            return validMeals;
        }
    }
}