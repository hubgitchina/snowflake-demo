package cn.com.ut.util;

import java.text.ParseException;

import cn.com.ut.core.common.util.CommonUtil;
import cn.com.ut.core.common.util.ExceptionUtil;

/**
 * Twitter_Snowflake<br>
 * SnowFlake的结构如下(每部分用-分开):<br>
 * 0 - 0000000000 0000000000 0000000000 0000000000 0 - 00000 - 00000 -
 * 000000000000 <br>
 * 1位标识，由于long基本类型在Java中是带符号的，最高位是符号位，正数是0，负数是1，所以id一般是正数，最高位是0<br>
 * 41位时间截(毫秒级)，注意，41位时间截不是存储当前时间的时间截，而是存储时间截的差值（当前时间截 - 开始时间截)
 * 得到的值），这里的的开始时间截，一般是我们的id生成器开始使用的时间，由我们程序来指定的（如下下面程序IdWorker类的startTime属性）。41位的时间截，可以使用69年，年T
 * = (1L << 41) / (1000L * 60 * 60 * 24 * 365) = 69<br>
 * 10位的数据机器位，可以部署在1024个节点，包括5位datacenterId和5位workerId<br>
 * 12位序列，毫秒内的计数，12位的计数顺序号支持每个节点每毫秒(同一机器，同一时间截)产生4096个ID序号<br>
 * 加起来刚好64位，为一个Long型。<br>
 * SnowFlake的优点是，整体上按照时间自增排序，并且整个分布式系统内不会产生ID碰撞(由数据中心ID和机器ID作区分)，并且效率较高，经测试，SnowFlake每秒能够产生26万ID左右。
 */
public class SnowflakeIdFactory {

	// ==============================Fields===========================================
	/** 开始时间截 (2018-01-01 00:00:00) */
	private final long twepoch = 1514736000000L;

	/** 机器id所占的位数 */
	private final long workerIdBits = 4L;

	/** 数据标识id所占的位数 */
	private final long datacenterIdBits = 6L;

	/** 支持的最大机器id，结果是15 (这个移位算法可以很快的计算出几位二进制数所能表示的最大十进制数) */
	private final long maxWorkerId = -1L ^ (-1L << workerIdBits);

	/** 支持的最大数据标识id，结果是63 */
	private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);

	/** 序列在id中占的位数 */
	private final long sequenceBits = 12L;

	/** 生成序列的掩码，这里为4095 (0b111111111111=0xfff=4095) */
	private final long sequenceMask = -1L ^ (-1L << sequenceBits);

	/** 毫秒内序列(0~4095) */
	private long sequence = 0L;

	/** 上次生成ID的时间截 */
	private long lastTimestamp = -1L;

	/**
	 * 订单前缀
	 */
	private static String PREFIX_ORDER = "1";

	/**
	 * 交易流水号前缀
	 */
	private static String PREFIX_TRADE = "9";

	/**
	 * 退款流水号前缀
	 */
	private static String PREFIX_REFUND = "8";

	// ==============================Constructors=====================================
	/**
	 * 构造函数
	 */
	public SnowflakeIdFactory() {

	}

	// ==============================Methods==========================================

	/**
	 * 获得下一个ID (该方法是线程安全的)
	 * 
	 * @return SnowflakeId
	 */
	public synchronized String nextId() {

		long timestamp = timeGen();

		// 如果当前时间小于上一次ID生成的时间戳，说明系统时钟回退过这个时候应当抛出异常
		if (timestamp < lastTimestamp) {
			ExceptionUtil.throwServiceException(
					String.format("系统时钟回退异常，距离当前时间落后  %d 毫秒", lastTimestamp - timestamp));
		}

		// 如果是同一时间生成的，则进行毫秒内序列
		if (lastTimestamp == timestamp) {
			sequence = (sequence + 1) & sequenceMask;
			// 毫秒内序列溢出
			if (sequence == 0) {
				// 阻塞到下一个毫秒,获得新的时间戳
				timestamp = tilNextMillis(lastTimestamp);
			}
		}
		// 时间戳改变，毫秒内序列重置
		else {
			sequence = 0L;
		}

		// 上次生成ID的时间截
		lastTimestamp = timestamp;

		// 通过位运算生成新的ID
		long generateId = ((timestamp - twepoch) << sequenceBits) | sequence;

		return Long.toString(generateId);
	}

	/**
	 * 获得下一个ID (该方法是线程安全的)
	 * 
	 * @return SnowflakeId
	 */
	public synchronized String nextSequence() {

		long timestamp = timeGen();

		// 如果当前时间小于上一次ID生成的时间戳，说明系统时钟回退过这个时候应当抛出异常
		if (timestamp < lastTimestamp) {
			ExceptionUtil.throwServiceException(
					String.format("系统时钟回退异常，距离当前时间落后  %d 毫秒", lastTimestamp - timestamp));
		}

		// 如果是同一时间生成的，则进行毫秒内序列
		if (lastTimestamp == timestamp) {
			sequence = (sequence + 1) & sequenceMask;
			// 毫秒内序列溢出
			if (sequence == 0) {
				// 阻塞到下一个毫秒,获得新的时间戳
				timestamp = tilNextMillis(lastTimestamp);
			}
		}
		// 时间戳改变，毫秒内序列重置
		else {
			sequence = 0L;
		}

		// 上次生成ID的时间截
		lastTimestamp = timestamp;

		// 计算时间戳差值
		long timeInterval = timestamp - twepoch;

		// 计算时间戳差值二进制所占位数，序列数sequence向左移动的位数与之相同
		long timeShift = String.valueOf(Long.toBinaryString(timeInterval)).length();

		// 通过位运算生成新的ID
		long generateId = (sequence << timeShift) | timeInterval;

		// 该方法为同步方法，线程安全，使用StringBuilder性能更好
		StringBuilder sb = new StringBuilder(String.valueOf(generateId));

		// 返回反转后的新ID，降低生成ID的连续性，可读性，增加安全性
		return sb.reverse().toString();
	}

	/**
	 * 根据业务标识和分片分表标识生成新的流水号
	 * 
	 * @param prefix
	 * @param sliceTable
	 * @return
	 */
	public String createBusinessNo(String prefix, String sliceTable) {

		String generateId = nextSequence();

		// 确定长度，减少内存再扩展
		StringBuilder sb = new StringBuilder(1 + generateId.length() + sliceTable.length());

		if (CommonUtil.isEmpty(sliceTable)) {
			// 拼接业务标识，便于数据检索
			sb.append(prefix).append(generateId);
		} else {
			// 拼接业务标识，分片分表标识，便于数据检索
			sb.append(prefix).append(generateId).append(sliceTable);
		}

		return sb.toString();
	}

	/**
	 * 根据分片分表标识生成新的流水号
	 * 
	 * @param sliceTable
	 * @return
	 */
	public String createBusinessNo(String sliceTable) {

		String generateId = nextSequence();

		// 确定长度，减少内存再扩展
		StringBuilder sb = new StringBuilder(generateId.length() + sliceTable.length());

		if (CommonUtil.isEmpty(sliceTable)) {
			sb.append(generateId);
		} else {
			// 拼接分片分表标识，便于数据检索
			sb.append(generateId).append(sliceTable);
		}

		return sb.toString();
	}

	/**
	 * 生成订单编号
	 * 
	 * @param sliceTable
	 * @return
	 */
	public String createOrderNo(String sliceTable) {

		// return createBusinessNo(PREFIX_ORDER, sliceTable);
		return createBusinessNo(sliceTable);
	}

	/**
	 * 生成退款流水号
	 * 
	 * @param sliceTable
	 * @return
	 */
	public String createRefundNo(String sliceTable) {

		// return createBusinessNo(PREFIX_REFUND, sliceTable);
		return createBusinessNo(sliceTable);
	}

	/**
	 * 阻塞到下一个毫秒，直到获得新的时间戳
	 * 
	 * @param lastTimestamp
	 *            上次生成ID的时间截
	 * @return 当前时间戳
	 */
	protected long tilNextMillis(long lastTimestamp) {

		long timestamp = timeGen();
		while (timestamp <= lastTimestamp) {
			timestamp = timeGen();
		}
		return timestamp;
	}

	/**
	 * 返回以毫秒为单位的当前时间
	 * 
	 * @return 当前时间(毫秒)
	 */
	protected long timeGen() {

		return System.currentTimeMillis();
	}

	// ==============================Test=============================================

	/**
	 * 测试
	 * 
	 * @throws ParseException
	 */
	public static void main(String[] args) throws ParseException {

		SnowflakeIdFactory idWorker = new SnowflakeIdFactory();
		long start = System.currentTimeMillis();
		for (int i = 0; i < 10000000; i++) {
			String id = idWorker.nextSequence();
			long end = System.currentTimeMillis();
			String newId = id + "1664";
			// System.out.println("id为: " + newId);
			// System.out.println("id位数: " + newId.length());
			if ((end - start) >= 1000) {
				System.out.println("已耗时：" + (end - start));
				System.out.println("跳出循环，当前索引为: " + i);
				System.out.println("id为: " + newId);
				break;
			}
		}
	}

}
