package com.example.fitnesee;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;

import com.example.fitnesee.databinding.ActivityMealEntryBinding; // 导入生成的 DataBinding 类
// 注意：这里可能需要根据你的实际包名调整

import java.util.ArrayList;
import java.util.List;

public class MealEntryActivity extends AppCompatActivity {
    private static final String TAG = "MealEntryActivity";
    private ActivityMealEntryBinding binding; // 声明绑定对象
    private MealAdapter breakfastAdapter, lunchAdapter, dinnerAdapter;
    private NutritionDatabase nutritionDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 使用 DataBindingUtil 或 inflate 方法来绑定布局
        binding = ActivityMealEntryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot()); // 设置根视图

        Log.d(TAG, "Layout inflated successfully using DataBinding.");

        // 现在通过 binding 对象访问所有视图
        RecyclerView recyclerViewBreakfast = binding.recyclerViewBreakfast;
        Log.d(TAG, "recyclerViewBreakfast: " + (recyclerViewBreakfast == null ? "null" : "found"));

        RecyclerView recyclerViewLunch = binding.recyclerViewLunch;
        Log.d(TAG, "recyclerViewLunch: " + (recyclerViewLunch == null ? "null" : "found"));

        RecyclerView recyclerViewDinner = binding.recyclerViewDinner;
        Log.d(TAG, "recyclerViewDinner: " + (recyclerViewDinner == null ? "null" : "found"));

        MaterialButton addBreakfastButton = binding.addBreakfastButton;
        Log.d(TAG, "addBreakfastButton: " + (addBreakfastButton == null ? "null" : "found"));

        MaterialButton addLunchButton = binding.addLunchButton;
        Log.d(TAG, "addLunchButton: " + (addLunchButton == null ? "null" : "found"));

        MaterialButton addDinnerButton = binding.addDinnerButton;
        Log.d(TAG, "addDinnerButton: " + (addDinnerButton == null ? "null" : "found"));

        MaterialButton submitButton = binding.submitButton;
        Log.d(TAG, "submitButton: " + (submitButton == null ? "null" : "found"));

        MaterialButton viewLogsButton = binding.viewLogsButton;
        Log.d(TAG, "viewLogsButton: " + (viewLogsButton == null ? "null" : "found"));


        if (recyclerViewBreakfast == null || recyclerViewLunch == null || recyclerViewDinner == null ||
                addBreakfastButton == null || addLunchButton == null || addDinnerButton == null ||
                submitButton == null || viewLogsButton == null) {
            Toast.makeText(this, "界面初始化失败，请检查布局文件或 DataBinding 配置", Toast.LENGTH_LONG).show();
            Log.e(TAG, "One or more UI components failed to initialize with DataBinding. Aborting further setup.");
            return;
        }

        nutritionDb = new NutritionDatabase(this);

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

                    Intent intent = new Intent(MealEntryActivity.this, ResultActivity.class);
                    intent.putExtra("RESULT_TYPE", "DAILY_DATA");
                    intent.putExtra("DAILY_RESULT", result.toString());
                    startActivity(intent);
                }

                @Override
                public void onError(String errorMessage) {
                    Intent intent = new Intent(MealEntryActivity.this, ResultActivity.class);
                    intent.putExtra("RESULT_TYPE", "ERROR");
                    intent.putExtra("ERROR_MESSAGE", "错误: " + errorMessage);
                    startActivity(intent);
                }
            });
        });

        viewLogsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MealEntryActivity.this, LogActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (nutritionDb != null) {
            nutritionDb.close();
        }
        binding = null; // 解除绑定，避免内存泄漏
    }

    // MealAdapter 内部类保持不变
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
                    public void afterTextChanged(android.text.Editable s) {
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            meals.get(position).foodName = s.toString();
                            Log.d(TAG, "Food name updated at position " + position + ": " + s.toString());
                        }
                    }
                });

                editTextGrams.addTextChangedListener(new SimpleTextWatcher() {
                    @Override
                    public void afterTextChanged(android.text.Editable s) {
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            try {
                                double grams = s.toString().isEmpty() ? 0 : Double.parseDouble(s.toString());
                                if (grams < 0) {
                                    s.replace(0, s.length(), "0");
                                    Toast.makeText(MealEntryActivity.this, "克数不能为负数", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                meals.get(position).grams = grams;
                            } catch (NumberFormatException e) {
                                Toast.makeText(MealEntryActivity.this, "请输入有效的克数", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
            }
        }

        abstract class SimpleTextWatcher implements android.text.TextWatcher {
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