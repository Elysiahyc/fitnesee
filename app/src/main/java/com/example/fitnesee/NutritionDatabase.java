package com.example.fitnesee;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// 数据库帮助类，用于管理营养数据和用户日志
public class NutritionDatabase extends SQLiteOpenHelper {
    // 定义数据库相关常量
    private static final String DATABASE_NAME = "Nutrition.db"; // 数据库名称
    private static final int DATABASE_VERSION = 6; // 数据库版本号
    private static final String TABLE_FOOD = "food_nutrients"; // 食物营养表
    private static final String TABLE_USER = "user_profile"; // 用户资料表
    private static final String TABLE_LOG = "upload_log"; // 饮食日志表
    private static final String COLUMN_ID = "id"; // 主键ID
    private static final String COLUMN_NAME = "name"; // 食物名称
    private static final String COLUMN_PROTEIN = "protein"; // 蛋白质
    private static final String COLUMN_FAT = "fat"; // 脂肪
    private static final String COLUMN_CARB = "carb"; // 碳水化合物
    private static final String COLUMN_CALORIES = "calories"; // 热量
    private static final String USER_ID = "user_id"; // 用户ID
    private static final String USER_WEIGHT = "weight"; // 体重
    private static final String USER_HEIGHT = "height"; // 身高
    private static final String USER_AGE = "age"; // 年龄
    private static final String USER_GENDER = "gender"; // 性别
    private static final String USER_GOAL = "goal"; // 健身目标
    private static final String LOG_TIMESTAMP = "timestamp"; // 日志时间戳
    private static final String LOG_FOOD_NAME = "food_name"; // 日志中的食物名称
    private static final String LOG_GRAMS = "grams"; // 食物克数
    private static final String LOG_MEAL_TYPE = "meal_type"; // 餐类型
    private static final String TAG = "NutritionDatabase"; // 日志标签
    private static final long TASK_TIMEOUT_SECONDS = 60; // 任务超时时间（秒）

    private static final OkHttpClient client = new OkHttpClient(); // HTTP客户端实例

    // 构造函数，初始化数据库
    public NutritionDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // 创建数据库表结构
    @Override
    public void onCreate(SQLiteDatabase db) {
        // 创建食物营养表
        String createFoodTable = "CREATE TABLE " + TABLE_FOOD + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_NAME + " TEXT, " +
                COLUMN_PROTEIN + " REAL, " +
                COLUMN_FAT + " REAL, " +
                COLUMN_CARB + " REAL, " +
                COLUMN_CALORIES + " REAL)";
        db.execSQL(createFoodTable);

        // 创建用户资料表
        String createUserTable = "CREATE TABLE " + TABLE_USER + " (" +
                USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                USER_WEIGHT + " REAL, " +
                USER_HEIGHT + " REAL, " +
                USER_AGE + " INTEGER, " +
                USER_GENDER + " TEXT, " +
                USER_GOAL + " TEXT)";
        db.execSQL(createUserTable);

        // 创建饮食日志表
        String createLogTable = "CREATE TABLE " + TABLE_LOG + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                LOG_TIMESTAMP + " TEXT, " +
                LOG_FOOD_NAME + " TEXT, " +
                LOG_GRAMS + " REAL, " +
                LOG_MEAL_TYPE + " TEXT)";
        db.execSQL(createLogTable);
    }

    // 数据库升级时删除并重建表
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 删除现有表
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FOOD);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USER);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOG);
        // 重新创建表
        onCreate(db);
    }

    // 插入用户资料到数据库
    public void insertUserProfile(double weight, double height, int age, String gender, String goal) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(USER_WEIGHT, weight);
        values.put(USER_HEIGHT, height);
        values.put(USER_AGE, age);
        values.put(USER_GENDER, gender);
        values.put(USER_GOAL, goal);
        // 插入用户资料
        db.insert(TABLE_USER, null, values);
        db.close();
    }

    // 查询用户资料
    public UserProfile getUserProfile() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USER, new String[]{USER_WEIGHT, USER_HEIGHT, USER_AGE, USER_GENDER, USER_GOAL},
                null, null, null, null, null);
        UserProfile profile = null;
        // 提取用户资料数据
        if (cursor.moveToFirst()) {
            double weight = cursor.getDouble(cursor.getColumnIndexOrThrow(USER_WEIGHT));
            double height = cursor.getDouble(cursor.getColumnIndexOrThrow(USER_HEIGHT));
            int age = cursor.getInt(cursor.getColumnIndexOrThrow(USER_AGE));
            String gender = cursor.getString(cursor.getColumnIndexOrThrow(USER_GENDER));
            String goal = cursor.getString(cursor.getColumnIndexOrThrow(USER_GOAL));
            profile = new UserProfile(weight, height, age, gender, goal);
        }
        cursor.close();
        db.close();
        return profile;
    }

    // 触发每日食物数据获取
    public void fetchDailyFoodData(List<MealEntry> meals, OnDailyDataFetchedListener listener) {
        // 记录饮食日志
        logUploadData(meals);
        // 启动异步任务获取每日数据
        new FetchDailyFoodTask(listener).execute(meals);
    }

    // 记录饮食数据到日志表，处理重复记录并返回最新日志
    public List<LogEntry> logUploadData(List<MealEntry> meals) {
        SQLiteDatabase db = this.getWritableDatabase();
        // 设置时间格式并获取当前日期和时间（香港时区）
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM月dd日");
        dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Hong_Kong"));
        String currentDate = dateFormat.format(Calendar.getInstance(TimeZone.getTimeZone("Asia/Hong_Kong")).getTime());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss 'HKT'");
        timeFormat.setTimeZone(TimeZone.getTimeZone("Asia/Hong_Kong"));
        String currentTime = timeFormat.format(Calendar.getInstance(TimeZone.getTimeZone("Asia/Hong_Kong")).getTime());
        String timestamp = currentDate + " " + currentTime;

        List<LogEntry> latestLogs = new ArrayList<>();
        for (MealEntry meal : meals) {
            // 检查当天是否存在记录
            String whereClause = LOG_TIMESTAMP + " LIKE ? AND " + LOG_FOOD_NAME + " = ?";
            String[] whereArgs = {currentDate + "%", meal.foodName};
            Cursor cursor = db.query(TABLE_LOG, new String[]{COLUMN_ID, LOG_TIMESTAMP, LOG_FOOD_NAME, LOG_GRAMS, LOG_MEAL_TYPE},
                    whereClause, whereArgs, null, null, LOG_TIMESTAMP + " DESC LIMIT 1");

            ContentValues values = new ContentValues();
            values.put(LOG_TIMESTAMP, timestamp);
            values.put(LOG_FOOD_NAME, meal.foodName);
            values.put(LOG_GRAMS, meal.grams);
            values.put(LOG_MEAL_TYPE, meal.mealType != null ? meal.mealType : "unknown");

            if (cursor.moveToFirst()) {
                // 如果存在记录，更新最后一条记录
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
                db.update(TABLE_LOG, values, COLUMN_ID + "=?", new String[]{String.valueOf(id)});
                latestLogs.add(new LogEntry(timestamp, meal.foodName, meal.grams, meal.mealType != null ? meal.mealType : "unknown"));
            } else {
                // 如果不存在记录，插入新记录
                db.insert(TABLE_LOG, null, values);
                latestLogs.add(new LogEntry(timestamp, meal.foodName, meal.grams, meal.mealType != null ? meal.mealType : "unknown"));
            }
            cursor.close();
        }
        db.close();
        Log.d(TAG, "Logged upload data for date: " + currentDate + ", time: " + currentTime);
        return latestLogs;
    }

    // 获取所有上传日志
    public List<LogEntry> getUploadLogs() {
        List<LogEntry> logs = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        // 查询日志表，按时间戳降序排列
        Cursor cursor = db.query(TABLE_LOG, new String[]{LOG_TIMESTAMP, LOG_FOOD_NAME, LOG_GRAMS, LOG_MEAL_TYPE},
                null, null, null, null, LOG_TIMESTAMP + " DESC");

        // 遍历查询结果，构建日志列表
        while (cursor.moveToNext()) {
            String timestamp = cursor.getString(cursor.getColumnIndexOrThrow(LOG_TIMESTAMP));
            String foodName = cursor.getString(cursor.getColumnIndexOrThrow(LOG_FOOD_NAME));
            double grams = cursor.getDouble(cursor.getColumnIndexOrThrow(LOG_GRAMS));
            String mealType = cursor.getString(cursor.getColumnIndexOrThrow(LOG_MEAL_TYPE));
            logs.add(new LogEntry(timestamp, foodName, grams, mealType));
        }
        cursor.close();
        db.close();
        return logs;
    }

    // 从缓存中获取食物数据
    private FoodData getCachedFoodData(String foodName) {
        SQLiteDatabase db = this.getReadableDatabase();
        // 查询食物营养表，查找指定食物名称的数据
        Cursor cursor = db.query(TABLE_FOOD, new String[]{COLUMN_NAME, COLUMN_PROTEIN, COLUMN_FAT, COLUMN_CARB, COLUMN_CALORIES},
                COLUMN_NAME + "=?", new String[]{foodName}, null, null, null);
        FoodData foodData = null;
        if (cursor.moveToFirst()) {
            String name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME));
            double protein = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_PROTEIN));
            double fat = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_FAT));
            double carb = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_CARB));
            double calories = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_CALORIES));
            foodData = new FoodData(name, calories, protein, fat, carb);
        }
        cursor.close();
        db.close();
        return foodData;
    }

    // 将营养数据保存到本地数据库
    private synchronized void saveToLocalDatabase(String name, double calories, double protein, double fat, double carb) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, name);
        values.put(COLUMN_CALORIES, calories);
        values.put(COLUMN_PROTEIN, protein);
        values.put(COLUMN_FAT, fat);
        values.put(COLUMN_CARB, carb);
        // 插入或替换食物营养数据
        db.insertWithOnConflict(TABLE_FOOD, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    // 异步任务类，用于获取每日食物数据
    private class FetchDailyFoodTask extends AsyncTask<List<MealEntry>, Void, DailyFoodData> {
        private final OnDailyDataFetchedListener listener;
        private String errorMessage;

        FetchDailyFoodTask(OnDailyDataFetchedListener listener) {
            if (listener == null) throw new IllegalArgumentException("Listener cannot be null");
            this.listener = listener;
        }

        // 任务开始前记录日志
        @Override
        protected void onPreExecute() {
            Log.d(TAG, "Starting FetchDailyFoodTask");
        }

        // 后台执行任务，获取和处理每日数据
        @Override
        protected DailyFoodData doInBackground(List<MealEntry>... params) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            FutureTask<DailyFoodData> futureTask = new FutureTask<>(new Callable<DailyFoodData>() {
                @Override
                public DailyFoodData call() throws Exception {
                    List<MealEntry> meals = params[0];
                    // 初始化餐数据映射
                    Map<String, List<FoodData>> mealData = new HashMap<>();
                    mealData.put("breakfast", new ArrayList<>());
                    mealData.put("lunch", new ArrayList<>());
                    mealData.put("dinner", new ArrayList<>());
                    double totalCalories = 0, totalProtein = 0, totalFat = 0, totalCarb = 0;

                    // 遍历每餐，获取食物数据
                    for (MealEntry meal : meals) {
                        FoodData foodData = fetchFoodDataFromZhipu(meal.foodName, meal.grams);
                        if (foodData != null) {
                            String mealType = meal.mealType != null ? meal.mealType : "breakfast";
                            mealData.get(mealType).add(foodData);
                            totalCalories += foodData.calories;
                            totalProtein += foodData.protein;
                            totalFat += foodData.fat;
                            totalCarb += foodData.carb;
                        } else {
                            Log.w(TAG, "No data fetched for: " + meal.foodName);
                            // 如果API获取失败，使用默认数据
                            foodData = getDefaultFoodData(meal.foodName, meal.grams);
                            if (foodData != null) {
                                String mealType = meal.mealType != null ? meal.mealType : "breakfast";
                                mealData.get(mealType).add(foodData);
                                totalCalories += foodData.calories;
                                totalProtein += foodData.protein;
                                totalFat += foodData.fat;
                                totalCarb += foodData.carb;
                            }
                        }
                    }

                    // 获取用户资料并计算推荐热量
                    UserProfile profile = getUserProfile();
                    if (profile == null) {
                        throw new Exception("User profile not found");
                    }

                    double bmr = calculateBMR(profile.weight, profile.height, profile.age, profile.gender);
                    double activityFactor = "maintain".equals(profile.goal) ? 1.2 : "lose".equals(profile.goal) ? 1.1 : 1.375;
                    double recommendedCalories = bmr * activityFactor;

                    // 计算每餐热量
                    double breakfastCalories = mealData.get("breakfast").stream().mapToDouble(fd -> fd.calories).sum();
                    double lunchCalories = mealData.get("lunch").stream().mapToDouble(fd -> fd.calories).sum();
                    double dinnerCalories = mealData.get("dinner").stream().mapToDouble(fd -> fd.calories).sum();

                    // 获取个性化建议
                    String advice = fetchPersonalizedAdviceFromZhipu(totalCalories, recommendedCalories, breakfastCalories, lunchCalories, dinnerCalories, profile.goal);
                    List<FoodData> combinedFoodData = mealData.values().stream()
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                    return new DailyFoodData(combinedFoodData, totalCalories, totalProtein, totalFat, totalCarb, recommendedCalories, advice, breakfastCalories, lunchCalories, dinnerCalories);
                }
            });

            executor.execute(futureTask);
            try {
                // 设置超时并获取任务结果
                return futureTask.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                errorMessage = "Task interrupted: " + e.getMessage();
                Log.e(TAG, errorMessage, e);
                Thread.currentThread().interrupt();
                return null;
            } catch (TimeoutException e) {
                errorMessage = "Task timed out: " + e.getMessage();
                Log.e(TAG, errorMessage);
                futureTask.cancel(true);
                return null;
            } catch (Exception e) {
                errorMessage = "Task execution failed: " + e.getMessage();
                Log.e(TAG, errorMessage, e);
                return null;
            } finally {
                executor.shutdown();
            }
        }

        // 从Zhipu API获取食物营养数据
        private FoodData fetchFoodDataFromZhipu(String foodName, double grams) throws Exception {
            // 先尝试从缓存获取数据
            FoodData cachedData = getCachedFoodData(foodName);
            if (cachedData != null) {
                Log.d(TAG, "Using cached data for: " + foodName);
                return scaleFoodData(cachedData, grams);
            }

            // 构建API请求
            String apiUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
            String prompt = "Provide the nutrition data per 100g for the food '" + foodName + "' (translate to English if needed). Return in this exact format: 'Calories: X kcal, Protein: Y g, Fat: Z g, Carbohydrates: W g' where X, Y, Z, W are numbers.";
            JSONObject message = new JSONObject();
            try {
                message.put("role", "user");
                message.put("content", prompt);
            } catch (JSONException e) {
                Log.e(TAG, "Failed to create JSON message in fetchFoodData: " + e.getMessage(), e);
                return getDefaultFoodData(foodName, grams);
            }

            JSONArray messages = new JSONArray();
            messages.put(message);

            JSONObject requestBody = new JSONObject();
            try {
                requestBody.put("model", "glm-4");
                requestBody.put("messages", messages);
                requestBody.put("max_tokens", 200);
                requestBody.put("temperature", 0.7);
                requestBody.put("top_p", 0.9);
            } catch (JSONException e) {
                Log.e(TAG, "Failed to create JSON request body in fetchFoodData: " + e.getMessage(), e);
                return getDefaultFoodData(foodName, grams);
            }

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer " + BuildConfig.ZHIPU_API_KEY)
                    .post(RequestBody.create(MediaType.parse("application/json"), requestBody.toString()))
                    .build();

            // 发送请求并处理响应
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseText = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Zhipu API Response for Nutrition: " + responseText);
                    if (responseText.isEmpty()) {
                        Log.w(TAG, "Empty response from Zhipu API for " + foodName);
                        return getDefaultFoodData(foodName, grams);
                    }
                    double calories = extractNutrient(responseText, "Calories");
                    double protein = extractNutrient(responseText, "Protein");
                    double fat = extractNutrient(responseText, "Fat");
                    double carb = extractNutrient(responseText, "Carbohydrates");

                    if (calories == 0.0) {
                        Log.w(TAG, "Failed to fetch calories for " + foodName + ", using default data");
                        return getDefaultFoodData(foodName, grams);
                    }

                    // 保存到本地数据库
                    saveToLocalDatabase(foodName, calories, protein, fat, carb);
                    return scaleFoodData(new FoodData(foodName, calories, protein, fat, carb), grams);
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "No response body";
                    Log.e(TAG, "Zhipu API request failed in fetchFoodData: " + response.code() + " - " + response.message() + ", body: " + errorBody);
                    return getDefaultFoodData(foodName, grams);
                }
            } catch (IOException e) {
                Log.e(TAG, "Network error for " + foodName + ": " + e.getMessage(), e);
                return getDefaultFoodData(foodName, grams);
            }
        }

        // 从Zhipu API获取个性化建议
        private String fetchPersonalizedAdviceFromZhipu(double totalCalories, double recommendedCalories, double breakfastCalories, double lunchCalories, double dinnerCalories, String goal) {
            String apiUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
            // 获取当前时间并确定当前餐阶段
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Hong_Kong"));
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm 'HKT' 'on' yyyy-MM-dd");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Hong_Kong"));
            String currentTime = sdf.format(calendar.getTime());
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            String currentMealPhase = hour < 10 ? "breakfast" : hour < 16 ? "lunch" : "dinner";

            // 构建提示词
            String prompt = String.format(
                    "You are a nutrition and fitness expert. The current time is %s. A user has the following nutrition data:\n" +
                            "- Breakfast calories: %.1f kcal\n" +
                            "- Lunch calories: %.1f kcal\n" +
                            "- Dinner calories: %.1f kcal\n" +
                            "- Total calories consumed: %.1f kcal\n" +
                            "- Recommended daily calories: %.1f kcal\n" +
                            "- User's goal: %s (options: 'lose' for weight loss, 'gain' for muscle gain, 'maintain' for maintenance)\n" +
                            "Provide a concise and personalized advice in Chinese, including only the following:\n" +
                            "1. A simple comparison of total calories consumed vs. recommended calories (e.g., '您摄入的热量为X千卡，推荐热量为Y千卡').\n" +
                            "2. If not all meals are completed and there are remaining calories, suggest a specific meal plan for their next meal with example foods and approximate calorie counts (e.g., '建议下一餐：200g鸡胸肉约300千卡，150g米饭约195千卡，150g蔬菜约50千卡').\n" +
                            "3. If all meals are completed or it's too late for another meal, suggest a healthy diet plan for tomorrow (e.g., '明天的健康饮食建议：早餐：燕麦50g约190千卡，鸡蛋2个约136千卡；午餐：鸡胸肉200g约300千卡，米饭150g约195千卡；晚餐：三文鱼150g约300千卡，蔬菜150g约50千卡').\n" +
                            "4. Provide exercise suggestions based on their goal and calorie status:\n" +
                            "   - If they exceeded their recommended calories, suggest specific exercises to burn off the excess (e.g., '运动建议：跑步30分钟消耗约300千卡，或快走1小时消耗约200千卡').\n" +
                            "   - If they are within or below their calorie goal, suggest exercises to support their goal:\n" +
                            "     - For 'lose': Suggest cardio exercises (e.g., '运动建议：跑步30分钟消耗约300千卡，或游泳45分钟消耗约400千卡').\n" +
                            "     - For 'gain': Suggest strength training (e.g., '运动建议：力量训练，如深蹲、卧推，每次3组，每组10次').\n" +
                            "     - For 'maintain': Suggest moderate exercise (e.g., '运动建议：快走30分钟消耗约150千卡，或每周3次瑜伽').\n" +
                            "Ensure the advice is concise and practical, without using '#' headers.",
                    currentTime, breakfastCalories, lunchCalories, dinnerCalories, totalCalories, recommendedCalories, goal);

            // 构建请求消息
            JSONObject message = new JSONObject();
            try {
                message.put("role", "user");
                message.put("content", prompt);
            } catch (JSONException e) {
                Log.e(TAG, "Failed to create JSON message in fetchAdvice: " + e.getMessage(), e);
                return generateDefaultAdvice(totalCalories, recommendedCalories, breakfastCalories, lunchCalories, dinnerCalories, goal);
            }

            JSONArray messages = new JSONArray();
            messages.put(message);

            JSONObject requestBody = new JSONObject();
            try {
                requestBody.put("model", "glm-4");
                requestBody.put("messages", messages);
                requestBody.put("max_tokens", 500);
                requestBody.put("temperature", 0.7);
                requestBody.put("top_p", 0.9);
            } catch (JSONException e) {
                Log.e(TAG, "Failed to create JSON request body in fetchAdvice: " + e.getMessage(), e);
                return generateDefaultAdvice(totalCalories, recommendedCalories, breakfastCalories, lunchCalories, dinnerCalories, goal);
            }

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer " + BuildConfig.ZHIPU_API_KEY)
                    .post(RequestBody.create(MediaType.parse("application/json"), requestBody.toString()))
                    .build();

            // 发送请求并处理响应
            try (Response response = client.newCall(request).execute()) {
                String responseText = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "Zhipu API Response for Advice: " + responseText);
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Zhipu API request failed in fetchAdvice: " + response.code() + " - " + response.message() + ", body: " + responseText);
                    if (response.code() == 401) {
                        return "API 密钥无效，请检查 ZHIPU_API_KEY 或联系 Zhipu 支持";
                    } else if (response.code() == 402) {
                        return "Zhipu API 账户余额不足，请充值后重试";
                    }
                    return generateDefaultAdvice(totalCalories, recommendedCalories, breakfastCalories, lunchCalories, dinnerCalories, goal);
                }

                if (responseText.isEmpty()) {
                    Log.w(TAG, "Empty response from Zhipu API for advice");
                    return generateDefaultAdvice(totalCalories, recommendedCalories, breakfastCalories, lunchCalories, dinnerCalories, goal);
                }

                // 解析响应内容
                try {
                    JSONObject json = new JSONObject(responseText);
                    if (!json.has("choices")) {
                        Log.e(TAG, "Response JSON does not contain 'choices' key: " + responseText);
                        return generateDefaultAdvice(totalCalories, recommendedCalories, breakfastCalories, lunchCalories, dinnerCalories, goal);
                    }
                    JSONArray choices = json.getJSONArray("choices");
                    if (choices.length() == 0) {
                        Log.e(TAG, "Choices array is empty: " + responseText);
                        return generateDefaultAdvice(totalCalories, recommendedCalories, breakfastCalories, lunchCalories, dinnerCalories, goal);
                    }
                    JSONObject choice = choices.getJSONObject(0);
                    if (!choice.has("message")) {
                        Log.e(TAG, "Choice does not contain 'message' key: " + responseText);
                        return generateDefaultAdvice(totalCalories, recommendedCalories, breakfastCalories, lunchCalories, dinnerCalories, goal);
                    }
                    JSONObject messageObj = choice.getJSONObject("message");
                    if (!messageObj.has("content")) {
                        Log.e(TAG, "Message does not contain 'content' key: " + responseText);
                        return generateDefaultAdvice(totalCalories, recommendedCalories, breakfastCalories, lunchCalories, dinnerCalories, goal);
                    }
                    String content = messageObj.getString("content");
                    return content != null && !content.trim().isEmpty() ? content : "无法获取建议，请稍后重试";
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse Zhipu API response: " + e.getMessage() + ", response: " + responseText, e);
                    return generateDefaultAdvice(totalCalories, recommendedCalories, breakfastCalories, lunchCalories, dinnerCalories, goal);
                }
            } catch (IOException e) {
                Log.e(TAG, "Network error for advice: " + e.getMessage(), e);
                return generateDefaultAdvice(totalCalories, recommendedCalories, breakfastCalories, lunchCalories, dinnerCalories, goal);
            }
        }

        // 提取响应中的营养值
        private double extractNutrient(String responseText, String nutrient) {
            Log.d(TAG, "Extracting nutrient: " + nutrient + ", from response: " + responseText);
            // 使用正则表达式提取营养值
            String pattern = "(?i)" + Pattern.quote(nutrient) + ":\\s*(\\d+\\.?\\d*)\\s*(kcal|g)?";
            try {
                Pattern p = Pattern.compile(pattern);
                Matcher m = p.matcher(responseText);
                if (m.find()) {
                    double value = Double.parseDouble(m.group(1));
                    Log.d(TAG, "Extracted " + nutrient + ": " + value);
                    return value;
                }
                Log.w(TAG, "Failed to extract " + nutrient + " from: " + responseText);
                return 0.0;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid pattern for nutrient: " + nutrient + ", error: " + e.getMessage(), e);
                return 0.0;
            }
        }

        // 按克数缩放食物数据
        private FoodData scaleFoodData(FoodData foodData, double grams) {
            double ratio = grams / 100.0;
            return new FoodData(foodData.name, foodData.calories * ratio, foodData.protein * ratio,
                    foodData.fat * ratio, foodData.carb * ratio);
        }

        // 获取默认食物数据
        private FoodData getDefaultFoodData(String foodName, double grams) {
            double calories, protein, fat, carb;
            String name = foodName.toLowerCase();
            // 根据食物名称设置默认营养值
            if (name.contains("egg") || name.contains("鸡蛋")) {
                calories = 68; protein = 6.3; fat = 5.0; carb = 0.5;
            } else if (name.contains("pork") || name.contains("猪肉")) {
                calories = 250; protein = 26; fat = 15; carb = 0;
            } else if (name.contains("rice") || name.contains("米饭")) {
                calories = 130; protein = 2.7; fat = 0.3; carb = 28;
            } else {
                calories = 50; protein = 1; fat = 1; carb = 10;
            }
            saveToLocalDatabase(foodName, calories, protein, fat, carb);
            return scaleFoodData(new FoodData(foodName, calories, protein, fat, carb), grams);
        }

        // 生成默认建议
        private String generateDefaultAdvice(double totalCalories, double recommendedCalories, double breakfastCalories, double lunchCalories, double dinnerCalories, String goal) {
            StringBuilder advice = new StringBuilder();
            double remainingCalories = recommendedCalories - totalCalories;

            // 确定当前餐阶段和下一餐
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Hong_Kong"));
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            String currentMealPhase = hour < 10 ? "breakfast" : hour < 16 ? "lunch" : "dinner";
            String nextMeal = currentMealPhase.equals("breakfast") ? "lunch" : currentMealPhase.equals("lunch") ? "dinner" : null;
            int mealsCompleted = 0;
            if (breakfastCalories > 0) mealsCompleted++;
            if (lunchCalories > 0) mealsCompleted++;
            if (dinnerCalories > 0) mealsCompleted++;

            // 提供热量摄入对比
            advice.append("您摄入的热量为").append(String.format("%.1f", totalCalories)).append("千卡，推荐热量为").append(String.format("%.1f", recommendedCalories)).append("千卡\n");

            // 根据餐完成情况提供饮食建议
            if (mealsCompleted < 3 && nextMeal != null && remainingCalories > 0) {
                advice.append("建议下一餐（").append(nextMeal).append("）：\n");
                if ("lose".equals(goal)) {
                    advice.append("鸡胸肉200g约300千卡\n蔬菜沙拉150g约50千卡\n");
                } else if ("gain".equals(goal)) {
                    advice.append("牛肉200g约500千卡\n米饭200g约260千卡\n鸡蛋2个约136千卡\n");
                } else {
                    advice.append("鸡胸肉200g约300千卡\n米饭150g约195千卡\n蔬菜150g约50千卡\n");
                }
            } else {
                advice.append("明天的健康饮食建议：\n");
                if ("lose".equals(goal)) {
                    advice.append("早餐：燕麦50g约190千卡，鸡蛋2个约136千卡\n");
                    advice.append("午餐：鸡胸肉200g约300千卡，蔬菜150g约50千卡\n");
                    advice.append("晚餐：三文鱼150g约300千卡，蔬菜150g约50千卡\n");
                } else if ("gain".equals(goal)) {
                    advice.append("早餐：燕麦50g约190千卡，鸡蛋3个约204千卡\n");
                    advice.append("午餐：牛肉200g约500千卡，米饭200g约260千卡\n");
                    advice.append("晚餐：鸡胸肉200g约300千卡，红薯150g约130千卡\n");
                } else {
                    advice.append("早餐：燕麦50g约190千卡，鸡蛋2个约136千卡\n");
                    advice.append("午餐：鸡胸肉200g约300千卡，米饭150g约195千卡\n");
                    advice.append("晚餐：三文鱼150g约300千卡，蔬菜150g约50千卡\n");
                }
            }

            // 根据热量摄入提供运动建议
            advice.append("\n运动建议：\n");
            if (totalCalories > recommendedCalories) {
                double excessCalories = totalCalories - recommendedCalories;
                advice.append("您的热量摄入超标").append(String.format("%.1f", excessCalories)).append("千卡，建议以下运动：\n");
                advice.append("跑步30分钟消耗约300千卡\n快走1小时消耗约200千卡\n游泳45分钟消耗约400千卡\n");
            } else {
                if ("lose".equals(goal)) {
                    advice.append("跑步30分钟消耗约300千卡\n游泳45分钟消耗约400千卡\n");
                } else if ("gain".equals(goal)) {
                    advice.append("力量训练，如深蹲、卧推，每次3组，每组10次\n");
                } else {
                    advice.append("快走30分钟消耗约150千卡\n每周3次瑜伽\n");
                }
            }

            return advice.toString();
        }

        // 任务完成后通知监听器
        @Override
        protected void onPostExecute(DailyFoodData result) {
            Log.d(TAG, "Entering onPostExecute");
            if (listener != null) {
                if (result != null) {
                    listener.onDataFetched(result, result.totalCalories, result.totalProtein, result.totalFat, result.totalCarb, result.recommendedCalories, result.advice);
                } else {
                    String detailedError = errorMessage != null ? errorMessage : "Unable to fetch food data, please retry";
                    listener.onError(detailedError);
                }
            }
        }
    }

    // 计算基础代谢率（BMR）
    public double calculateBMR(double weight, double height, int age, String gender) {
        if ("male".equalsIgnoreCase(gender)) {
            return 88.362 + (13.397 * weight) + (4.799 * height) - (5.677 * age);
        } else {
            return 447.593 + (9.247 * weight) + (3.098 * height) - (4.330 * age);
        }
    }

    // 数据获取监听器接口
    public interface OnDailyDataFetchedListener {
        void onDataFetched(DailyFoodData dailyFoodData, double totalCalories, double totalProtein, double totalFat, double totalCarb, double recommendedCalories, String advice);
        void onError(String errorMessage);
    }

    // 食物数据类
    public static class FoodData {
        public String name;
        public double calories, protein, fat, carb;

        FoodData(String name, double calories, double protein, double fat, double carb) {
            this.name = name;
            this.calories = calories;
            this.protein = protein;
            this.fat = fat;
            this.carb = carb;
        }
    }

    // 餐记录类
    public static class MealEntry {
        public String foodName;
        public double grams;
        public String mealType;

        MealEntry(String foodName, double grams) {
            this.foodName = foodName;
            this.grams = grams;
            this.mealType = null;
        }

        MealEntry(String foodName, double grams, String mealType) {
            this.foodName = foodName;
            this.grams = grams;
            this.mealType = mealType;
        }
    }

    // 每日食物数据类
    public static class DailyFoodData {
        public List<FoodData> foodDataList;
        public double totalCalories, totalProtein, totalFat, totalCarb;
        public double recommendedCalories;
        public String advice;
        public double breakfastCalories, lunchCalories, dinnerCalories;

        DailyFoodData(List<FoodData> foodDataList, double totalCalories, double totalProtein, double totalFat, double totalCarb, double recommendedCalories, String advice, double breakfastCalories, double lunchCalories, double dinnerCalories) {
            this.foodDataList = foodDataList;
            this.totalCalories = totalCalories;
            this.totalProtein = totalProtein;
            this.totalFat = totalFat;
            this.totalCarb = totalCarb;
            this.recommendedCalories = recommendedCalories;
            this.advice = advice;
            this.breakfastCalories = breakfastCalories;
            this.lunchCalories = lunchCalories;
            this.dinnerCalories = dinnerCalories;
        }
    }

    // 用户资料类
    public static class UserProfile {
        public double weight, height;
        public int age;
        public String gender, goal;

        UserProfile(double weight, double height, int age, String gender, String goal) {
            this.weight = weight;
            this.height = height;
            this.age = age;
            this.gender = gender;
            this.goal = goal;
        }
    }

    // 日志记录类
    public static class LogEntry {
        public String timestamp;
        public String foodName;
        public double grams;
        public String mealType;

        LogEntry(String timestamp, String foodName, double grams, String mealType) {
            this.timestamp = timestamp;
            this.foodName = foodName;
            this.grams = grams;
            this.mealType = mealType;
        }
    }
}