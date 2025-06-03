package com.example.fitnesee;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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

public class NutritionDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "Nutrition.db";
    private static final int DATABASE_VERSION = 6;
    private static final String TABLE_FOOD = "food_nutrients";
    private static final String TABLE_USER = "user_profile";
    private static final String TABLE_LOG = "upload_log";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_PROTEIN = "protein";
    private static final String COLUMN_FAT = "fat";
    private static final String COLUMN_CARB = "carb";
    private static final String COLUMN_CALORIES = "calories";
    private static final String USER_ID = "user_id";
    private static final String USER_WEIGHT = "weight";
    private static final String USER_HEIGHT = "height";
    private static final String USER_AGE = "age";
    private static final String USER_GENDER = "gender";
    private static final String USER_GOAL = "goal";
    private static final String LOG_TIMESTAMP = "timestamp";
    private static final String LOG_FOOD_NAME = "food_name";
    private static final String LOG_GRAMS = "grams";
    private static final String LOG_MEAL_TYPE = "meal_type";
    private static final String ZHIPU_API_KEY = "9fc2d468ac024c6a94e74dc94ded7242.f1luPWTJsQ3Z3EGu";
    private static final String TAG = "NutritionDatabase";
    private static final long TASK_TIMEOUT_SECONDS = 60;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2000;

    public NutritionDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createFoodTable = "CREATE TABLE " + TABLE_FOOD + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_NAME + " TEXT, " +
                COLUMN_PROTEIN + " REAL, " +
                COLUMN_FAT + " REAL, " +
                COLUMN_CARB + " REAL, " +
                COLUMN_CALORIES + " REAL)";
        db.execSQL(createFoodTable);

        String createUserTable = "CREATE TABLE " + TABLE_USER + " (" +
                USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                USER_WEIGHT + " REAL, " +
                USER_HEIGHT + " REAL, " +
                USER_AGE + " INTEGER, " +
                USER_GENDER + " TEXT, " +
                USER_GOAL + " TEXT)";
        db.execSQL(createUserTable);

        String createLogTable = "CREATE TABLE " + TABLE_LOG + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                LOG_TIMESTAMP + " TEXT, " +
                LOG_FOOD_NAME + " TEXT, " +
                LOG_GRAMS + " REAL, " +
                LOG_MEAL_TYPE + " TEXT)";
        db.execSQL(createLogTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FOOD);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USER);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOG);
        onCreate(db);
    }

    public void insertUserProfile(double weight, double height, int age, String gender, String goal) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(USER_WEIGHT, weight);
        values.put(USER_HEIGHT, height);
        values.put(USER_AGE, age);
        values.put(USER_GENDER, gender);
        values.put(USER_GOAL, goal);
        db.insert(TABLE_USER, null, values);
        db.close();
    }

    public UserProfile getUserProfile() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USER, new String[]{USER_WEIGHT, USER_HEIGHT, USER_AGE, USER_GENDER, USER_GOAL},
                null, null, null, null, null);
        UserProfile profile = null;
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

    public void fetchDailyFoodData(List<MealEntry> meals, OnDailyDataFetchedListener listener) {
        logUploadData(meals);
        new FetchDailyFoodTask(listener).execute(meals);
    }

    private void logUploadData(List<MealEntry> meals) {
        SQLiteDatabase db = this.getWritableDatabase();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'HKT'");
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Hong_Kong"));
        String timestamp = sdf.format(Calendar.getInstance(TimeZone.getTimeZone("Asia/Hong_Kong")).getTime());

        for (MealEntry meal : meals) {
            ContentValues values = new ContentValues();
            values.put(LOG_TIMESTAMP, timestamp);
            values.put(LOG_FOOD_NAME, meal.foodName);
            values.put(LOG_GRAMS, meal.grams);
            values.put(LOG_MEAL_TYPE, meal.mealType);
            db.insert(TABLE_LOG, null, values);
        }
        db.close();
        Log.d(TAG, "Logged upload data for timestamp: " + timestamp);
    }

    public List<LogEntry> getUploadLogs() {
        List<LogEntry> logs = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_LOG, new String[]{LOG_TIMESTAMP, LOG_FOOD_NAME, LOG_GRAMS, LOG_MEAL_TYPE},
                null, null, null, null, LOG_TIMESTAMP + " DESC");

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

    private FoodData getCachedFoodData(String foodName) {
        SQLiteDatabase db = this.getReadableDatabase();
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

    private synchronized void saveToLocalDatabase(String name, double calories, double protein, double fat, double carb) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, name);
        values.put(COLUMN_CALORIES, calories);
        values.put(COLUMN_PROTEIN, protein);
        values.put(COLUMN_FAT, fat);
        values.put(COLUMN_CARB, carb);
        db.insertWithOnConflict(TABLE_FOOD, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    private class FetchDailyFoodTask extends AsyncTask<List<MealEntry>, Void, DailyFoodData> {
        private final OnDailyDataFetchedListener listener;
        private String errorMessage;

        FetchDailyFoodTask(OnDailyDataFetchedListener listener) {
            if (listener == null) throw new IllegalArgumentException("Listener cannot be null");
            this.listener = listener;
        }

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "Starting FetchDailyFoodTask");
        }

        @Override
        protected DailyFoodData doInBackground(List<MealEntry>... params) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            FutureTask<DailyFoodData> futureTask = new FutureTask<>(new Callable<DailyFoodData>() {
                @Override
                public DailyFoodData call() throws Exception {
                    List<MealEntry> meals = params[0];
                    Map<String, List<FoodData>> mealData = new HashMap<>();
                    mealData.put("breakfast", new ArrayList<>());
                    mealData.put("lunch", new ArrayList<>());
                    mealData.put("dinner", new ArrayList<>());
                    double totalCalories = 0, totalProtein = 0, totalFat = 0, totalCarb = 0;

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
                        }
                    }

                    UserProfile profile = getUserProfile();
                    if (profile == null) {
                        throw new Exception("User profile not found");
                    }

                    double bmr = calculateBMR(profile.weight, profile.height, profile.age, profile.gender);
                    double activityFactor = "maintain".equals(profile.goal) ? 1.2 : "lose".equals(profile.goal) ? 1.1 : 1.375;
                    double recommendedCalories = bmr * activityFactor;

                    double breakfastCalories = mealData.get("breakfast").stream().mapToDouble(fd -> fd.calories).sum();
                    double lunchCalories = mealData.get("lunch").stream().mapToDouble(fd -> fd.calories).sum();
                    double dinnerCalories = mealData.get("dinner").stream().mapToDouble(fd -> fd.calories).sum();

                    String advice = fetchPersonalizedAdviceFromZhipu(totalCalories, recommendedCalories, breakfastCalories, lunchCalories, dinnerCalories, profile.goal);
                    List<FoodData> combinedFoodData = mealData.values().stream()
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                    return new DailyFoodData(combinedFoodData, totalCalories, totalProtein, totalFat, totalCarb, recommendedCalories, advice, breakfastCalories, lunchCalories, dinnerCalories);
                }
            });

            executor.execute(futureTask);
            try {
                return futureTask.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                errorMessage = "Task interrupted: " + e.getMessage();
                Log.e(TAG, errorMessage, e);
                Thread.currentThread().interrupt();
                return null;
            } catch (TimeoutException e) {
                errorMessage = "Task timed out: Network request did not complete within " + TASK_TIMEOUT_SECONDS + " seconds";
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

        private FoodData fetchFoodDataFromZhipu(String foodName, double grams) throws Exception {
            FoodData cachedData = getCachedFoodData(foodName);
            if (cachedData != null) {
                Log.d(TAG, "Using cached data for: " + foodName);
                return scaleFoodData(cachedData, grams);
            }

            HttpURLConnection conn = null;
            BufferedReader reader = null;
            int retryCount = 0;
            while (retryCount < MAX_RETRIES) {
                try {
                    String apiUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
                    URL url = new URL(apiUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Authorization", "Bearer " + ZHIPU_API_KEY);

                    String prompt = "Provide the nutrition data per 100g for the food '" + foodName + "' (translate to English if needed). Return in this exact format: 'Calories: X kcal, Protein: Y g, Fat: Z g, Carbohydrates: W g' where X, Y, Z, W are numbers.";
                    JSONObject message = new JSONObject();
                    message.put("role", "user");
                    message.put("content", prompt);

                    JSONArray messages = new JSONArray();
                    messages.put(message);

                    JSONObject requestBody = new JSONObject();
                    requestBody.put("model", "glm-4");
                    requestBody.put("messages", messages);
                    requestBody.put("max_tokens", 200);
                    requestBody.put("temperature", 0.7);
                    requestBody.put("top_p", 0.9);

                    Log.d(TAG, "Zhipu API Request Body for Nutrition: " + requestBody.toString());
                    OutputStream os = conn.getOutputStream();
                    os.write(requestBody.toString().getBytes("UTF-8"));
                    os.flush();
                    os.close();

                    int responseCode = conn.getResponseCode();
                    Log.d(TAG, "Zhipu API Response Code for Nutrition: " + responseCode);
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        String responseText = response.toString();
                        Log.d(TAG, "Zhipu API Raw Response for Nutrition: " + responseText);

                        if (responseText == null || responseText.trim().isEmpty()) {
                            throw new Exception("Empty response from Zhipu API for nutrition data");
                        }

                        JSONObject json = new JSONObject(responseText);
                        if (!json.has("choices") || json.getJSONArray("choices").length() == 0) {
                            throw new Exception("No choices found in API response for nutrition data");
                        }

                        JSONArray choices = json.getJSONArray("choices");
                        String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
                        if (content == null || content.trim().isEmpty()) {
                            content = choices.getJSONObject(0).optString("text", "");
                        }
                        Log.d(TAG, "Extracted Content for Nutrition: " + content);

                        double calories = extractNutrient(content, "Calories");
                        double protein = extractNutrient(content, "Protein");
                        double fat = extractNutrient(content, "Fat");
                        double carb = extractNutrient(content, "Carbohydrates");

                        if (calories == 0.0) {
                            Log.w(TAG, "Failed to fetch calories for " + foodName + ", using default data");
                            return getDefaultFoodData(foodName, grams);
                        }

                        saveToLocalDatabase(foodName, calories, protein, fat, carb);
                        return scaleFoodData(new FoodData(foodName, calories, protein, fat, carb), grams);
                    } else {
                        reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                        StringBuilder errorResponse = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            errorResponse.append(line);
                        }
                        throw new Exception("Zhipu API request failed for nutrition data, status: " + responseCode + ", error: " + errorResponse.toString());
                    }
                } catch (Exception e) {
                    retryCount++;
                    Log.w(TAG, "Zhipu API attempt " + retryCount + "/" + MAX_RETRIES + " failed for nutrition data: " + e.getMessage());
                    if (retryCount == MAX_RETRIES) {
                        Log.w(TAG, "Max retries reached for nutrition data, using default data");
                        return getDefaultFoodData(foodName, grams);
                    }
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Log.w(TAG, "Retry delay interrupted, skipping delay: " + ie.getMessage());
                        Thread.currentThread().interrupt();
                    }
                } finally {
                    if (reader != null) try { reader.close(); } catch (Exception e) { Log.e(TAG, "Error closing reader", e); }
                    if (conn != null) conn.disconnect();
                }
            }
            return getDefaultFoodData(foodName, grams);
        }

        private String fetchPersonalizedAdviceFromZhipu(double totalCalories, double recommendedCalories, double breakfastCalories, double lunchCalories, double dinnerCalories, String goal) {
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            int retryCount = 0;
            while (retryCount < MAX_RETRIES) {
                try {
                    String apiUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
                    URL url = new URL(apiUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Authorization", "Bearer " + ZHIPU_API_KEY);

                    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Hong_Kong"));
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm 'HKT' on yyyy-MM-dd");
                    sdf.setTimeZone(TimeZone.getTimeZone("Asia/Hong_Kong"));
                    String currentTime = sdf.format(calendar.getTime());
                    int hour = calendar.get(Calendar.HOUR_OF_DAY);

                    String currentMealPhase = hour < 10 ? "breakfast" : hour < 16 ? "lunch" : "dinner";

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

                    JSONObject message = new JSONObject();
                    message.put("role", "user");
                    message.put("content", prompt);

                    JSONArray messages = new JSONArray();
                    messages.put(message);

                    JSONObject requestBody = new JSONObject();
                    requestBody.put("model", "glm-4");
                    requestBody.put("messages", messages);
                    requestBody.put("max_tokens", 500);
                    requestBody.put("temperature", 0.7);
                    requestBody.put("top_p", 0.9);

                    Log.d(TAG, "Zhipu API Request Body for Advice: " + requestBody.toString());
                    OutputStream os = conn.getOutputStream();
                    os.write(requestBody.toString().getBytes("UTF-8"));
                    os.flush();
                    os.close();

                    int responseCode = conn.getResponseCode();
                    Log.d(TAG, "Zhipu API Response Code for Advice: " + responseCode);
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        String responseText = response.toString();
                        Log.d(TAG, "Zhipu API Raw Response for Advice: " + responseText);

                        if (responseText == null || responseText.trim().isEmpty()) {
                            throw new Exception("Empty response from Zhipu API for advice");
                        }

                        JSONObject json = new JSONObject(responseText);
                        if (!json.has("choices") || json.getJSONArray("choices").length() == 0) {
                            throw new Exception("No choices found in API response for advice");
                        }

                        JSONArray choices = json.getJSONArray("choices");
                        String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
                        if (content == null || content.trim().isEmpty()) {
                            content = choices.getJSONObject(0).optString("text", "");
                        }
                        Log.d(TAG, "Extracted Advice: " + content);
                        return content;
                    } else {
                        reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                        StringBuilder errorResponse = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            errorResponse.append(line);
                        }
                        throw new Exception("Zhipu API request failed for advice, status: " + responseCode + ", error: " + errorResponse.toString());
                    }
                } catch (Exception e) {
                    retryCount++;
                    Log.w(TAG, "Zhipu API attempt " + retryCount + "/" + MAX_RETRIES + " failed for advice: " + e.getMessage());
                    if (retryCount == MAX_RETRIES) {
                        Log.w(TAG, "Max retries reached for advice, using default advice");
                        return generateDefaultAdvice(totalCalories, recommendedCalories, breakfastCalories, lunchCalories, dinnerCalories, goal);
                    }
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Log.w(TAG, "Retry delay interrupted, skipping delay: " + ie.getMessage());
                        Thread.currentThread().interrupt();
                    }
                } finally {
                    if (reader != null) try { reader.close(); } catch (Exception e) { Log.e(TAG, "Error closing reader", e); }
                    if (conn != null) conn.disconnect();
                }
            }
            return generateDefaultAdvice(totalCalories, recommendedCalories, breakfastCalories, lunchCalories, dinnerCalories, goal);
        }

        private double extractNutrient(String responseText, String nutrient) {
            String pattern = "(?i)" + nutrient + ":\\s*(\\d+\\.?\\d*)\\s*(kcal|g)?";
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(responseText);
            if (m.find()) {
                double value = Double.parseDouble(m.group(1));
                Log.d(TAG, "Extracted " + nutrient + ": " + value);
                return value;
            }
            Log.w(TAG, "Failed to extract " + nutrient + " from: " + responseText);
            return 0.0;
        }

        private FoodData scaleFoodData(FoodData foodData, double grams) {
            double ratio = grams / 100.0;
            return new FoodData(foodData.name, foodData.calories * ratio, foodData.protein * ratio,
                    foodData.fat * ratio, foodData.carb * ratio);
        }

        private FoodData getDefaultFoodData(String foodName, double grams) {
            double calories, protein, fat, carb;
            String name = foodName.toLowerCase();
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

        private String generateDefaultAdvice(double totalCalories, double recommendedCalories, double breakfastCalories, double lunchCalories, double dinnerCalories, String goal) {
            StringBuilder advice = new StringBuilder();
            double remainingCalories = recommendedCalories - totalCalories;

            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Hong_Kong"));
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            String currentMealPhase = hour < 10 ? "breakfast" : hour < 16 ? "lunch" : "dinner";
            String nextMeal = currentMealPhase.equals("breakfast") ? "lunch" : currentMealPhase.equals("lunch") ? "dinner" : null;
            int mealsCompleted = 0;
            if (breakfastCalories > 0) mealsCompleted++;
            if (lunchCalories > 0) mealsCompleted++;
            if (dinnerCalories > 0) mealsCompleted++;

            advice.append("您摄入的热量为").append(String.format("%.1f", totalCalories)).append("千卡，推荐热量为").append(String.format("%.1f", recommendedCalories)).append("千卡\n");

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

        @Override
        protected void onPostExecute(DailyFoodData result) {
            Log.d(TAG, "Entering onPostExecute");
            if (listener != null) {
                if (result != null) {
                    listener.onDataFetched(result, result.totalCalories, result.totalProtein, result.totalFat, result.totalCarb, result.recommendedCalories, result.advice);
                } else {
                    String detailedError = errorMessage != null ? errorMessage : "Unable to fetch food data, please retry";
                    if (errorMessage != null && errorMessage.contains("status: 402")) {
                        detailedError += "\n原因：API 余额不足，请联系管理员充值或更换密钥。";
                    }
                    listener.onError(detailedError);
                }
            }
        }
    }

    public double calculateBMR(double weight, double height, int age, String gender) {
        if ("male".equalsIgnoreCase(gender)) {
            return 88.362 + (13.397 * weight) + (4.799 * height) - (5.677 * age);
        } else {
            return 447.593 + (9.247 * weight) + (3.098 * height) - (4.330 * age);
        }
    }

    public interface OnDailyDataFetchedListener {
        void onDataFetched(DailyFoodData dailyFoodData, double totalCalories, double totalProtein, double totalFat, double totalCarb, double recommendedCalories, String advice);
        void onError(String errorMessage);
    }

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