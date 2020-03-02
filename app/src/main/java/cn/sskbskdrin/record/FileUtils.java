package cn.sskbskdrin.record;

import android.os.Environment;

import java.io.File;
import java.text.SimpleDateFormat;

/**
 * 文件处理工具类
 */
public class FileUtils {

    private static final String MAIN_DIR_NAME = "/AudioVideo/";

    private static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM_dd_HH_mm_ss");

    public FileUtils() {
    }

    public static String getRandomFile(String ext) {
        String fileName = simpleDateFormat.format(System.currentTimeMillis());


        String name = getSdcardPath() + MAIN_DIR_NAME + fileName + ext;
        File file = new File(name);
        File parentFile = file.getParentFile();
        if (!parentFile.exists()) {
            parentFile.mkdirs();
        }
        if (file.exists()) {
            file.delete();
        }
        return name;
    }

    /**
     * 获取sdcard路径
     */
    private static String getSdcardPath() {
        return Environment.getExternalStorageDirectory().getPath();
    }

}
