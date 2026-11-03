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
package com.sevtinge.hyperceiler.module.hook.systemframework.corepatch;

import com.sevtinge.hyperceiler.utils.prefs.PrefsUtils;

import de.robv.android.xposed.XC_MethodHook;

public class ReturnConstant extends XC_MethodHook {
    private final String prefsKey;
    private final Object value;

    public ReturnConstant(String prefsKey, Object value) {
        this.prefsKey = prefsKey;
        this.value = value;
    }

    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        super.beforeHookedMethod(param);
        // 配置已由 Provider 在进程启动时载入 mPrefsMap，这里只做内存读取，
        // 不再每次回调 reload 文件。
        if (PrefsUtils.getBooleanRaw(prefsKey, true)) {
            param.setResult(value);
        }
    }
}
