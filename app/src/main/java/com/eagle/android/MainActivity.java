package com.eagle.android;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.eagle.android.service.ChartA11yService;

public class MainActivity extends AppCompatActivity {
    private TextView tvStatus;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
//        Intent i = new Intent(MainActivity.this, SocketServerService.class);
//        i.putExtra("name", "socket");
//        MainActivity.this.startService(i);
        Intent accessibilityIntent = new Intent(MainActivity.this, ChartA11yService.class);
        accessibilityIntent.putExtra("name", "accessibility");
        MainActivity.this.startService(accessibilityIntent);
        TextView textView = findViewById(R.id.myTextView);
//        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 在这里处理超链接的点击事件，例如打开网页
                String url = "http://www.baidu.com";
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                startActivity(intent);
            }
        });

        tvStatus = findViewById(R.id.tvA11yStatus);
        Button btnOpen = findViewById(R.id.btnOpenA11y);
        Button btnPreview = findViewById(R.id.btnOpenChartPreview);

        refreshA11yStatus();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }

        btnPreview.setOnClickListener(v -> {
            Intent previewIntent = new Intent(MainActivity.this, ChartPreviewActivity.class);
            startActivity(previewIntent);
        });
    }
    @Override
    protected void onResume() {
        super.onResume();
        refreshA11yStatus();
    }

    private void refreshA11yStatus() {
        boolean enabled = isA11yServiceEnabled(this, ChartA11yService.class);
        tvStatus.setText(enabled ? "无障碍服务：已启用 ✅（按系统无障碍按钮试试）"
                : "无障碍服务：未启用 ❌（点下面按钮去开启）");
    }

//    private void openA11ySettings() {
//        // 尝试直接打开“本应用无障碍详情”，失败则退回总列表
//        try {
//            Intent i = new Intent(Settings.ACTION_ACC);
//            i.setData(Uri.parse("package:" + getPackageName()));
//            startActivity(i);
//        } catch (Exception e) {
//            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
//        }
//    }

    private static boolean isA11yServiceEnabled(Context ctx, Class<? extends AccessibilityService> svc) {
        try {
            String enabled = Settings.Secure.getString(
                    ctx.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (enabled == null) return false;
            ComponentName cn = new ComponentName(ctx, svc);
            String flat1 = cn.flattenToString();       // com.eagle.android/.service.ChartA11yService
            String flat2 = cn.flattenToShortString();  // com.eagle.android/.service.ChartA11yService（有些 ROM 只匹配这个）
            return enabled.contains(flat1) || enabled.contains(flat2);
        } catch (Throwable t) {
            return false;
        }
    }
}