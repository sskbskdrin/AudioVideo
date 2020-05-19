package cn.sskbskdrin.lib.face;

import android.util.Log;

/**
 * Created by keayuan on 2020/5/18.
 *
 * @author keayuan
 */
class LogUtil {
    public static void w(String tag, String s) {
        Log.w(tag, s);
    }

    public static void d(String tag, String s) {
        Log.d(tag, s);
    }

    public static void e(String tag, String s) {
        Log.e(tag, s);
    }

    public static boolean isLoggable() {
        return true;
    }
}
