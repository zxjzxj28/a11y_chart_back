package com.eagle.android.a11y;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

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

        // keys（与 xml 中保持一致）
        public static final String KEY_VOL_SWITCH = "feature_shortcut_volume_enabled";
        public static final String KEY_VOICE_SWITCH = "feature_shortcut_voice_enabled";
        public static final String KEY_GESTURE_SWITCH = "feature_shortcut_gesture_enabled";

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

            // 三个开关
            SwitchPreferenceCompat volSwitch = findPreference(KEY_VOL_SWITCH);
            SwitchPreferenceCompat voiceSwitch = findPreference(KEY_VOICE_SWITCH);
            SwitchPreferenceCompat gestureSwitch = findPreference(KEY_GESTURE_SWITCH);

            // 其他两个独立开关（如果需要在这里做联动，可在此添加监听）
            // SwitchPreferenceCompat chartQa = findPreference(KEY_OPEN_CHART_QA);
            // SwitchPreferenceCompat sortByData = findPreference(KEY_SORT_BY_DATA);

            // 入口点击 → 跳到对应 Activity（仅当开关开启）
            if (volConfig != null) {
                volConfig.setOnPreferenceClickListener(p -> {
                    if (volSwitch != null && volSwitch.isChecked()) {
                        startActivity(new Intent(requireContext(), VolumeKeyConfigActivity.class));
                    }
                    return true;
                });
            }
            if (voiceConfig != null) {
                voiceConfig.setOnPreferenceClickListener(p -> {
                    if (voiceSwitch != null && voiceSwitch.isChecked()) {
                        startActivity(new Intent(requireContext(), VoiceCommandConfigActivity.class));
                    }
                    return true;
                });
            }
            if (gestureConfig != null) {
                gestureConfig.setOnPreferenceClickListener(p -> {
                    if (gestureSwitch != null && gestureSwitch.isChecked()) {
                        startActivity(new Intent(requireContext(), GestureConfigActivity.class));
                    }
                    return true;
                });
            }

            // 根据开关禁用/启用对应入口
            updateChildrenEnabledState(volSwitch, volConfig);
            updateChildrenEnabledState(voiceSwitch, voiceConfig);
            updateChildrenEnabledState(gestureSwitch, gestureConfig);

            // 监听开关变化实时更新可点击状态
            if (volSwitch != null) {
                volSwitch.setOnPreferenceChangeListener((p, v) -> {
                    updateChildrenEnabledState(volSwitch, volConfig, (Boolean) v);
                    return true;
                });
            }
            if (voiceSwitch != null) {
                voiceSwitch.setOnPreferenceChangeListener((p, v) -> {
                    updateChildrenEnabledState(voiceSwitch, voiceConfig, (Boolean) v);
                    return true;
                });
            }
            if (gestureSwitch != null) {
                gestureSwitch.setOnPreferenceChangeListener((p, v) -> {
                    updateChildrenEnabledState(gestureSwitch, gestureConfig, (Boolean) v);
                    return true;
                });
            }
        }

        private void updateChildrenEnabledState(SwitchPreferenceCompat sw, Preference child) {
            if (sw != null && child != null) child.setEnabled(sw.isChecked());
        }

        private void updateChildrenEnabledState(SwitchPreferenceCompat sw, Preference child, boolean newVal) {
            if (child != null) child.setEnabled(newVal);
        }
    }
}
