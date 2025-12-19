package com.eagle.android.a11y;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.eagle.android.R;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar ab = getSupportActionBar();
        if (ab != null) ab.setDisplayHomeAsUpEnabled(true);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        public static final String KEY_OPEN_CHART_QA = "feature_chart_voice_qa_enabled";
        public static final String KEY_SORT_BY_DATA = "feature_sort_by_data_enabled";

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            // 使用统一的 SP 名称，便于服务端读取
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit().apply();
            getPreferenceManager().setSharedPreferencesName("a11y_prefs");
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            // 找到三个“进入二级配置”的入口
            Preference volConfig = findPreference("pref_volume_config");
            Preference voiceConfig = findPreference("pref_voice_config");
            Preference gestureConfig = findPreference("pref_gesture_config");

            // 其他两个独立开关（如果需要在这里做联动，可在此添加监听）
            // SwitchPreferenceCompat chartQa = findPreference(KEY_OPEN_CHART_QA);
            // SwitchPreferenceCompat sortByData = findPreference(KEY_SORT_BY_DATA);

            // 入口点击 → 跳到对应 Activity（开关状态不再限制入口访问）
            if (volConfig != null) {
                volConfig.setOnPreferenceClickListener(p -> {
                    startActivity(new Intent(requireContext(), VolumeKeyConfigActivity.class));
                    return true;
                });
            }
            if (voiceConfig != null) {
                voiceConfig.setOnPreferenceClickListener(p -> {
                    startActivity(new Intent(requireContext(), VoiceCommandConfigActivity.class));
                    return true;
                });
            }
            if (gestureConfig != null) {
                gestureConfig.setOnPreferenceClickListener(p -> {
                    startActivity(new Intent(requireContext(), GestureConfigActivity.class));
                    return true;
                });
            }
        }
    }
}
