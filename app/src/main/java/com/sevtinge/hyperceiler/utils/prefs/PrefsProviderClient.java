/*
 * This file is part of HyperCeiler.

 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.

 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.

 * Copyright (C) 2023-2025 HyperCeiler Contributions
 */
package com.sevtinge.hyperceiler.utils.prefs;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

import com.sevtinge.hyperceiler.provider.SharedPrefsProvider;
import com.sevtinge.hyperceiler.utils.ContextUtils;
import com.sevtinge.hyperceiler.utils.log.XposedLogUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * 通过 ContentProvider 读取模块配置。
 * <p>
 * 不使用 XSharedPreferences：它依赖模块元数据声明（xposedminversion &gt; 92 或
 * xposedsharedprefs）才会走框架托管目录，而该机制将在 LSPosed 2.3.0 移除；
 * 退回旧路径则要靠普通文件读取，实测会被 per-app SELinux category 拦死。
 * <p>
 * ContentProvider 走 Binder，不受 app_data_file category 限制。
 */
public final class PrefsProviderClient {
    private static final String TAG = "[HyperCeiler][Prefs]";
    private static final String METHOD_GET_ALL = "getAll";
    private static final Uri PREFS_URI = Uri.parse("content://" + SharedPrefsProvider.AUTHORITY);

    private PrefsProviderClient() {
    }

    /**
     * 读取全部配置。失败时返回 null，调用方应保留空 map 并走默认值。
     */
    public static Map<String, Object> loadAll() {
        Bundle bundle = queryWithContext();
        String via = "context";
        if (bundle == null) {
            bundle = queryWithActivityManager();
            via = "binder";
        }
        if (bundle == null) {
            XposedLogUtils.logE(TAG, "loadAll", "cannot reach SharedPrefsProvider");
            return null;
        }
        Map<String, Object> result = decode(bundle);
        XposedLogUtils.logI(TAG, "loadAll via " + via + ": " + result.size() + " keys");
        return result;
    }

    private static Bundle queryWithContext() {
        try {
            // zygote 阶段拿不到 Context，此时返回 null 交给 binder 兜底。
            Context context = ContextUtils.getContextNoError(ContextUtils.FLAG_ALL);
            if (context == null) return null;
            return context.getContentResolver().call(PREFS_URI, METHOD_GET_ALL, null, null);
        } catch (Throwable t) {
            XposedLogUtils.logD(TAG, "queryWithContext", String.valueOf(t));
            return null;
        }
    }

    /**
     * zygote 阶段没有 Context，直接通过 ActivityManager 拿到 IContentProvider 再调用。
     * 全部走反射，避免编译期依赖 @hide API。
     */
    private static Bundle queryWithActivityManager() {
        Object provider = null;
        try {
            Class<?> activityManagerClass = Class.forName("android.app.ActivityManager");
            Object activityManager = activityManagerClass.getMethod("getService").invoke(null);
            if (activityManager == null) return null;

            Class<?> iActivityManager = Class.forName("android.app.IActivityManager");
            Method getContentProviderExternal = iActivityManager.getMethod(
                "getContentProviderExternal", String.class, int.class, IBinder.class, String.class);
            Object holder = getContentProviderExternal.invoke(
                activityManager, SharedPrefsProvider.AUTHORITY, 0, null, "*");
            if (holder == null) return null;

            Object rawProvider = holder.getClass().getField("provider").get(holder);
            if (rawProvider == null) return null;
            provider = rawProvider;

            Class<?> iContentProvider = Class.forName("android.content.IContentProvider");
            Method call = iContentProvider.getMethod(
                "call", String.class, String.class, String.class, String.class, Bundle.class);
            Object result = call.invoke(provider, null, SharedPrefsProvider.AUTHORITY,
                METHOD_GET_ALL, null, null);
            return result instanceof Bundle ? (Bundle) result : null;
        } catch (Throwable t) {
            XposedLogUtils.logD(TAG, "queryWithActivityManager", String.valueOf(t));
            return null;
        } finally {
            if (provider != null) {
                try {
                    Class<?> iContentProvider = Class.forName("android.content.IContentProvider");
                    // best-effort：释放 provider 引用，失败无影响
                    iContentProvider.getMethod("asBinder").invoke(provider);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Map<String, Object> decode(Bundle bundle) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String prefixedKey : bundle.keySet()) {
            int sep = prefixedKey.indexOf(':');
            if (sep <= 0) continue;
            String type = prefixedKey.substring(0, sep);
            String key = prefixedKey.substring(sep + 1);
            try {
                switch (type) {
                    case "b" -> result.put(key, bundle.getBoolean(prefixedKey));
                    case "i" -> result.put(key, bundle.getInt(prefixedKey));
                    case "s" -> result.put(key, bundle.getString(prefixedKey));
                    case "ss" -> {
                        ArrayList<String> list = bundle.getStringArrayList(prefixedKey);
                        result.put(key, list == null ? new LinkedHashSet<String>() : new LinkedHashSet<>(list));
                    }
                    default -> {
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return result;
    }
}
