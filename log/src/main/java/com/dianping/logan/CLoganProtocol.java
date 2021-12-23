package com.dianping.logan;

import android.util.Log;

public class CLoganProtocol {

    private static CLoganProtocol sCLoganProtocol;
    private static boolean sIsCloganOk;

    private static final String TAG = "CLoganProtocol";

    static {
        try {
            System.loadLibrary("logan");
            sIsCloganOk = true;
            Log.d(TAG, "C库已装载完成");
        } catch (Throwable e) {
            sIsCloganOk = false;
            Log.e(TAG, "C库装载失败。", e);
        }
    }

    public static boolean isCloganSuccess() {
        return sIsCloganOk;
    }

    public static CLoganProtocol newInstance() {
        if (sCLoganProtocol == null) {
            synchronized (CLoganProtocol.class) {
                if (sCLoganProtocol == null) {
                    sCLoganProtocol = new CLoganProtocol();
                }
            }
        }
        return sCLoganProtocol;
    }

    /**
     * 初始化文件目录和最大文件大小
     *
     * @param cache_path     指定缓存<code>MMAP</code>的目录文件
     * @param dir_path       指定日志文件夹目录
     * @param max_file       指定最大文件大小
     * @param encrypt_key_16 指定128位的文件加密key
     * @param encrypt_iv_16  128位的文件加密iv
     * @return 结果码
     * @see ConstantCode
     */
    public native int clogan_init(String cache_path, String dir_path, int max_file, String encrypt_key_16, String encrypt_iv_16);

    /**
     * 打开一个文件的写入
     *
     * @param file_name 文件名称
     * @return 结果码
     * @see ConstantCode
     */
    public native int clogan_open(String file_name);

    /**
     * 是否为debug环境。debug环境将输出过程日志到控制台中
     *
     * @param is_debug debug 1为开启 0为关闭 默认为0
     */
    public native void clogan_debug(boolean is_debug);

    /**
     * 写入数据 按照顺序和类型传值
     *
     * @param flag        日志类型
     * @param log         日志内容
     * @param local_time  日志发生的本地时间（时间戳）
     * @param thread_name 线程名称
     * @param thread_id   线程id
     * @param is_main     是否为主线程，0为是主线程，1位非主线程
     * @return 结果码
     * @see ConstantCode
     */
    public native int clogan_write(int flag, String log, long local_time, String thread_name, long thread_id, int is_main);

    /**
     * 强制写入文件。建议在崩溃或者退出程序的时候调用
     */
    public native void clogan_flush();
}
