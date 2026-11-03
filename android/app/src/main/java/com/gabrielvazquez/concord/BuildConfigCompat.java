package com.gabrielvazquez.concord;

import android.content.Context;
import android.content.pm.PackageInfo;

final class BuildConfigCompat {
    private BuildConfigCompat() {}
    static PackageInfo info(Context c) { try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0); } catch (Exception e) { return null; } }
    static String version(Context c) { PackageInfo i = info(c); return i != null && i.versionName != null ? i.versionName : "1.0"; }
    static int code(Context c) { PackageInfo i = info(c); return i != null ? (int) i.getLongVersionCode() : 0; }
}
