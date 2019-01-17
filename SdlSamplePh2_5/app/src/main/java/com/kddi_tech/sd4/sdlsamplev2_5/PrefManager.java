/**
 Copyright 2018 KDDI Technology Corp.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.kddi_tech.sd4.sdlsamplev2_5;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * SharedPreferencesのマネージャクラスです
 * インスタンスの生成はgetInstance(Context)を使用してください
 */
public class PrefManager {
    private static PrefManager prefManager;
    private SharedPreferences preferences;
    private SharedPreferences.Editor editor;
    private Context context;

    private PrefManager(Context context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        editor = preferences.edit();
        this.context = context;
    }

    /**
     * ※※※※※
     * インスタンスを生成します
     * @param context コンテキスト
     * @return マネージャクラス
     */
    public static synchronized PrefManager getInstance(Context context) {
        if (prefManager == null) {
            prefManager = new PrefManager(context);
        }
        return prefManager;
    }

    /**
     * ※※※※※
     * Setterです
     * @param key 保存キー
     * @param val キーに紐づけて保存したい値
     */
    public void write(String key, String val){
        editor.putString(key, val);
        editor.commit();
    }
    public void write(String key, int val){
        editor.putInt(key, val);
        editor.commit();
    }
    public void write(String key, Boolean val){
        editor.putBoolean(key, val);
        editor.commit();
    }

    /**
     * ※※※※※
     * Getterです
     * @param key 取得したいキー
     * @param defaultValue キーに紐づく値がない場合の初期値
     * @return defaultValueと同じデータ型の値
     */
    public String read(String key, String defaultValue) {
        return preferences.getString(key, defaultValue);
    }
    public int read(String key, int defaultValue) {
        return preferences.getInt(key, defaultValue);
    }
    public Boolean read(String key, Boolean defaultValue) {
        return preferences.getBoolean(key,defaultValue);
    }

    /**
     * ※※※※※
     * キーに紐づく値を削除します
     * @param key 削除したいキー
     */
    public void remove(String key) {
        if (editor != null) {
            editor.remove(key).commit();
        }
    }

    /**
     * SharedPreferenceに保存されている全てのkey/valueを削除します
     */
    public void removeAll() {
        editor.clear().commit();
    }


    /**
     * ※※※※※
     * リソースIDに設定されているnameをキーとして文字列を保存する
     * @param resourceId 保存したいリソースID
     * @param val 保存したい文字列情報
     */
    public void setPrefByStr(int resourceId, String val) {
        write(context.getResources().getResourceEntryName(resourceId),val);
    }
    public void setPrefByBool(int resourceId, Boolean val) {
        write(context.getResources().getResourceEntryName(resourceId),val);
    }

    /**
     * ※※※※※
     * リソースIDに設定されているnameをキーとして文字列を取得するする
     * @param resourceId 取得したいリソースID
     * @param val リソースIDに紐づくデータが保持されていなかった場合の、デフォルト返却値
     * @return 文字列情報
     */
    public String getPrefByStr(int resourceId, String val) {
        return read(context.getResources().getResourceEntryName(resourceId),val);
    }
    public Boolean getPrefByBool(int resourceId, Boolean val) {
        return read(context.getResources().getResourceEntryName(resourceId),val);
    }
}
