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
package com.sevtinge.hyperceiler.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;

import com.sevtinge.hyperceiler.module.base.tool.AppsTool;
import com.sevtinge.hyperceiler.utils.prefs.PrefsUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SharedPrefsProvider extends ContentProvider {

    public static final String AUTHORITY = "com.sevtinge.hyperceiler.provider.sharedprefs";
    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    SharedPreferences prefs;

    static {
        uriMatcher.addURI(AUTHORITY, "string/*/", 0);
        uriMatcher.addURI(AUTHORITY, "string/*/*", 1);
        uriMatcher.addURI(AUTHORITY, "integer/*/*", 2);
        uriMatcher.addURI(AUTHORITY, "boolean/*/*", 3);
        uriMatcher.addURI(AUTHORITY, "stringset/*", 4);
        uriMatcher.addURI(AUTHORITY, "test/*", 5);
        uriMatcher.addURI(AUTHORITY, "shortcut_icon/*", 6);
    }

    @Override
    public boolean onCreate() {
        try {
            prefs = PrefsUtils.getSharedPrefs(getContext(), true);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        List<String> parts = uri.getPathSegments();
        // Log.e("parts", String.valueOf(parts));
        MatrixCursor cursor = new MatrixCursor(new String[]{"data"});

        switch (uriMatcher.match(uri)) {
            case 0 -> {
                cursor.newRow().add("data", prefs.getString(parts.get(1), ""));
                return cursor;
            }
            case 1 -> {
                cursor.newRow().add("data", prefs.getString(parts.get(1), parts.get(2)));
                return cursor;
            }
            case 2 -> {
                cursor.newRow().add("data", prefs.getInt(parts.get(1), Integer.parseInt(parts.get(2))));
                return cursor;
            }
            case 3 -> {
                cursor.newRow().add("data", prefs.getBoolean(parts.get(1), Integer.parseInt(parts.get(2)) == 1) ? 1 : 0);
                return cursor;
            }
            case 4 -> {
                Set<String> strings = prefs.getStringSet(parts.get(1), new LinkedHashSet<>());
                for (String str : strings) cursor.newRow().add("data", str);
                return cursor;
            }
        }
        return null;
    }

    @Override
    public AssetFileDescriptor openAssetFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        if (getContext() == null) return null;

        List<String> parts = uri.getPathSegments();
        if (uriMatcher.match(uri) == 5) {
            String filename = null;
            if ("0".equals(parts.get(1))) filename = "test0.png";
            else if ("1".equals(parts.get(1))) filename = "test1.mp3";
            else if ("2".equals(parts.get(1))) filename = "test2.mp4";
            else if ("3".equals(parts.get(1)) || "5".equals(parts.get(1))) filename = "test3.txt";
            else if ("4".equals(parts.get(1))) filename = "test4.zip";

            AssetFileDescriptor afd = null;
            if (filename != null) try {
                afd = getContext().getAssets().openFd(filename);
            } catch (Throwable t) {
                Log.i("afd", String.valueOf(t));
            }
            return afd;
        } else if (uriMatcher.match(uri) == 6) {
            Context context = AppsTool.getProtectedContext(getContext());
            File file = new File(context.getFilesDir() + "/shortcuts/" + parts.get(1) + "_shortcut.png");
            if (!file.exists()) return null;
            return new AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
        }

        return null;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    /**
     * 批量读取入口：hook 进程用它一次性拿到全部配置，
     * 避免 3000+ 个开关读取点变成 IPC。
     * <p>
     * 键值以类型前缀编码，保证 Bundle 能承载四种 prefs 类型：
     * b:/i:/s:/ss: 分别对应 boolean/int/String/Set&lt;String&gt;。
     */
    @Override
    public Bundle call(@NonNull String method, String arg, Bundle extras) {
        if (!"getAll".equals(method)) return super.call(method, arg, extras);

        SharedPreferences sp = prefs != null ? prefs : PrefsUtils.mSharedPreferences;
        if (sp == null) {
            Log.e("prefs", "call(getAll): prefs is null");
            return null;
        }

        Bundle out = new Bundle();
        try {
            for (Map.Entry<String, ?> entry : sp.getAll().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Boolean b) {
                    out.putBoolean("b:" + key, b);
                } else if (value instanceof Integer i) {
                    out.putInt("i:" + key, i);
                } else if (value instanceof String s) {
                    out.putString("s:" + key, s);
                } else if (value instanceof Set<?> set) {
                    ArrayList<String> list = new ArrayList<>(set.size());
                    for (Object item : set) if (item instanceof String str) list.add(str);
                    out.putStringArrayList("ss:" + key, list);
                }
            }
        } catch (Throwable t) {
            Log.e("prefs", "call(getAll) failed", t);
            return null;
        }
        return out;
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

}
