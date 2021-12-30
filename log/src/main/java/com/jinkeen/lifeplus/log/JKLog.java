package com.jinkeen.lifeplus.log;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.jinkeen.lifeplus.log.listener.OnLogProtocolStatusListener;
import com.jinkeen.lifeplus.log.nativ.LogConfig;
import com.jinkeen.lifeplus.log.nativ.LogControlCenterService;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * 日志记录与上传的操作类。
 * <ul>
 *     <li>该类中所有的方法均已在异步线程内进行工作，因此不会对<code>UI</code>线程有任何影响，且保证对所有线程的安全控制。</li>
 *     <li>从第一条日志记录开始，往后每隔24小时会自动创建一个新的日志永久记录文件，且在创建新文件之时，程序会自动将旧缓存刷入对应的记录文件中，且保证日志条目不会丢失。</li>
 *     <li>磁盘中的日志永久记录文件有效保存时间取决于<code>{@link LogConfig#getSaveDays()}</code>，过期后文件将自动被移除</li>
 * </ul>
 */
public final class JKLog {

    private static LogControlCenterService sLogControlCenter;
    private static ServiceConnection connection;

    /**
     * （必须）初始化，否则后续所有方法就无法执行。
     *
     * @param context 当前运行时上下文
     * @param config  日志基本配置信息
     */
    public static void init(Context context, LogConfig config) {
        final Intent intent = new Intent(context, LogControlCenterService.class);
        intent.putExtra(LogConfig.EXTRA_CONFIG, config);
        connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                if (service instanceof LogControlCenterService.ControlCenterBinder)
                    sLogControlCenter = ((LogControlCenterService.ControlCenterBinder) service).getService();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    /**
     * 在本地记录一条日志。
     * <br/>
     * 请慎重指定日志类型，若指定的类型在本地从没有被记录过，则将自动创建一个新的记录条目。
     *
     * @param type 日志类型，由记录者自行定义。<i>每次只能记录一个类型，不支持多个</i>
     * @param log  具体日志内容。
     */
    public static void w(int type, String log) {
        if (null == sLogControlCenter) throw new NullPointerException("请先初始化JKLog");
        sLogControlCenter.write(log, type);
    }

    /**
     * 在本地记录一条带有异常信息的日志
     * <br/>
     * 请慎重指定日志类型，若指定的类型在本地从没有被记录过，则将自动创建一个新的记录条目。
     *
     * @param type 日志类型，由记录者自行定义。<i>每次只能记录一个类型，不支持多个</i>
     * @param log  具体日志内容
     * @param tr   异常对象。
     */
    public static void e(int type, String log, Throwable tr) {
        if (null == sLogControlCenter) throw new NullPointerException("请先初始化JKLog");
        final StringBuilder builder = new StringBuilder(log);
        final StringWriter sWriter = new StringWriter();
        final PrintWriter pWriter = new PrintWriter(sWriter);
        tr.printStackTrace(pWriter);
        final String msg = sWriter.toString();
        try {
            sWriter.close();
            pWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        builder.append('\n');
        builder.append(msg.replaceAll("[\\r\\t]", ""));
        sLogControlCenter.write(builder.toString(), type);
    }

    /**
     * 立即写入日志文件
     */
    public static void f() {
        if (null == sLogControlCenter) throw new NullPointerException("请先初始化JKLog");
        sLogControlCenter.flush();
    }

    /**
     * 停止在本地的日志记录工作。将现有队列中的日志写入完成，并不再接收新的日志写入。
     *
     * @param context 当前运行时上下文
     * @param isFlush 是否在停止前将缓存队列中的日志强制写入到日志文件中
     */
    public static void quit(Context context, boolean isFlush) {
        if (null == sLogControlCenter) throw new NullPointerException("请先初始化JKLog");
        sLogControlCenter.quit(isFlush);
        context.unbindService(connection);
    }

    public static void setOnLogProtocolStatusListener(OnLogProtocolStatusListener listener) {
        if (null == sLogControlCenter) throw new NullPointerException("请先初始化JKLog");
        sLogControlCenter.setOnLogProtocolStatusListener(listener);
    }

    private static final SimpleDateFormat mSimpleFormat = new SimpleDateFormat("yyyyMMddHHmm", Locale.CHINA);

    /**
     * 立即上传日志信息到服务端
     *
     * @param recentDays 最近几天的日志？即：以调用时的时间为基准，往前数<code>recentDays</code>天。若超过本地已记录的最早日志时间，将自动上传本地已记录的所有日志。
     */
    public static long fastUp(int recentDays) {
        if (null == sLogControlCenter) throw new NullPointerException("请先初始化JKLog");
        final long currentTime = System.currentTimeMillis();
        return sLogControlCenter.up(new int[]{}, currentTime - recentDays * LogConfig.DAY, currentTime);
    }

    /**
     * 立即上传指定的日志信息到服务端，将按照具体的时间范围进行精细化的筛选。
     * <br/>
     * 若没有指定日志类型，则默认上传筛选范围内所有的日志信息，<i>这可能会占用较大量的网络流量</i>。
     *
     * @param types     指定要上传的日志类型
     * @param beginTime 开始的具体时间，格式为：<code><b><i>yyyyMMddHHmm</i></b></code>，若超过本地已记录的最早日志时间，将自动按本地记录的最早时间来算。
     * @param endTime   结束的具体时间，格式同<code>beginTime</code>，若超过本地记录的最晚日志时间，将自动按照本地记录的最晚日志时间来算。
     */
    public static long fastUp(int[] types, String beginTime, String endTime) {
        if (null == sLogControlCenter) throw new NullPointerException("请先初始化JKLog");
        try {
            final long b = Objects.requireNonNull(mSimpleFormat.parse(beginTime)).getTime();
            final long e = Objects.requireNonNull(mSimpleFormat.parse(endTime)).getTime();
            return sLogControlCenter.up(types, b, e);
        } catch (ParseException | NullPointerException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * 周期性（重复）的上传日志到服务端，除<code>{@link Cycle#FIXED_TIME}</code>外，其他周期性均以该方法第一次被执行时为时间基准。
     * <br/>
     * 周期性任务不接受多次执行，若多次调用，将始终按第一次为准。
     *
     * @param cycle  周期性的类型，取<code>{@link Cycle}</code>中定义的常量之一。
     * @param cValue 指定周期的具体数值，当<code>cycle=FIXED_TIME</code>时，时间默认采用24小时制。
     */
    public static long regularUp(Cycle cycle, int cValue) {
        if (null == sLogControlCenter) throw new NullPointerException("请先初始化JKLog");
        return 0L;
    }

    /** 周期性的常量标识 */
    public enum Cycle {

        /** 按天，即每24小时一个周期。 */
        DAY,

        /** 按小时 */
        HOUR,

        /** 按秒 */
        SECOND,

        /** 固定时间。即指定每天固定的时间为周期 */
        FIXED_TIME
    }

    /**
     * 停止指定的正在进行中的上传任务，若任务未完成，即刻停止后续的动作。
     *
     * @param taskId 由上传任务开始时生成的唯一任务ID
     */
    public static void stopUp(long taskId) {}

    /**
     * 立即停止所有正在进行中的上传任务，包括即时性的和周期性的。
     */
    public static void stopAllUp() {}
}
