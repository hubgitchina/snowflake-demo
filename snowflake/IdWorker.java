package cn.com.ut.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.google.common.net.InetAddresses;

import cn.com.ut.core.common.util.CollectionUtil;

/**
 * twitter的snowflake算法，全局唯一ID生成
 * 
 * @author wuxiaohua
 * @since 2017年3月23日 上午9:43:48
 */
@Component
public class IdWorker implements InitializingBean {

	@Autowired
	private Environment env;

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
	private static String PREFIX_RETURN = "8";

	/**
	 * 起始的时间戳
	 */
	private long startStmp = -1L;

	/**
	 * 每一部分占用的位数
	 */
	private final static long SEQUENCE_BIT = 12; // 序列号占用的位数
	private final static long DATACENTER_BIT = 2;// 数据中心占用的位数
	private final static long WORKER_BIT = 8; // 机器标识占用的位数

	/**
	 * 每一部分的最大值
	 */
	private final static long MAX_DATACENTER_NUM = -1L ^ (-1L << DATACENTER_BIT);
	private final static long MAX_WORKER_NUM = -1L ^ (-1L << WORKER_BIT);
	private final static long MAX_SEQUENCE = -1L ^ (-1L << SEQUENCE_BIT);

	/**
	 * 每一部分向左的位移
	 */
	private final static long WORKER_LEFT = SEQUENCE_BIT;
	private final static long DATACENTER_LEFT = SEQUENCE_BIT + WORKER_BIT;
	private final static long TIMESTMP_LEFT = DATACENTER_LEFT + DATACENTER_BIT;

	private long datacenterId = -1L; // 数据中心
	private long workerId = -1L; // 机器标识
	private long sequence = 0L; // 序列号
	private long lastStmp = -1L;// 上一次时间戳

	public IdWorker() {
	}

	@Override
	public void afterPropertiesSet() throws Exception {

		datacenterId = getDataCenterId();
		workerId = getWorkerId();
		startStmp = getStartStmp();

		if (datacenterId > MAX_DATACENTER_NUM || datacenterId < 0) {
			throw new IllegalArgumentException(
					"datacenterId can't be greater than MAX_DATACENTER_NUM or less than 0");
		}
		if (workerId > MAX_WORKER_NUM || workerId < 0) {
			throw new IllegalArgumentException(
					"workerId can't be greater than MAX_WORKER_NUM or less than 0");
		}
	}

	/**
	 * 生成订单编号
	 */
	public String genOrderSequence() {

		return genSequence(PREFIX_ORDER);
	}

	/**
	 * 生成支付流水号
	 */
	public String genTradeSequence() {

		return genSequence(PREFIX_TRADE);
	}

	/**
	 * 生成退款流水号
	 */
	public String genReturnSequence() {

		return genSequence(PREFIX_RETURN);
	}

	/**
	 * 生成全局唯一ID
	 * 
	 * @param prefix
	 *            前缀
	 * @param number
	 *            数量
	 * @return
	 */
	public List<String> genSequences(String prefix, int number) {

		List<String> sequences = new ArrayList<String>();
		if (number < 1)
			number = 1;
		if (prefix == null)
			prefix = "";
		for (int i = 0; i < number; i++) {
			sequences.add(prefix + nextId());
		}
		return sequences;
	}

	/**
	 * 生成全局唯一ID
	 * 
	 * @param prefix
	 *            前缀
	 * @return
	 */
	public String genSequence(String prefix) {

		return genSequences(prefix, 1).get(0);
	}

	/**
	 * 产生下一个ID
	 *
	 * @return
	 */
	public synchronized long nextId() {

		long currStmp = getCurrStmp();
		if (currStmp < lastStmp) {
			throw new RuntimeException("Clock moved backwards. Refusing to generate id");
		}

		if (currStmp == lastStmp) {
			// 相同毫秒内，序列号自增
			sequence = (sequence + 1) & MAX_SEQUENCE;
			// 同一毫秒的序列数已经达到最大
			if (sequence == 0L) {
				currStmp = getNextMill();
			}
		} else {
			// 不同毫秒内，序列号置为0
			sequence = 0L;
		}

		lastStmp = currStmp;

		return (currStmp - startStmp) << TIMESTMP_LEFT // 时间戳部分
				| datacenterId << DATACENTER_LEFT // 数据中心部分
				| workerId << WORKER_LEFT // 机器标识部分
				| sequence; // 序列号部分
	}

	/**
	 * 获取最新毫秒时间戳
	 * 
	 * @return
	 */
	private long getNextMill() {

		long mill = getCurrStmp();
		while (mill <= lastStmp) {
			mill = getCurrStmp();
		}
		return mill;
	}

	/**
	 * 获取当前时间戳
	 * 
	 * @return
	 */
	private long getCurrStmp() {

		return System.currentTimeMillis();
	}

	/**
	 * 从配置获取起始时间戳
	 * 
	 * @return
	 */
	private long getStartStmp() {

		String timestamp = env.getProperty("gid.timestamp");
		return Long.parseLong(timestamp);
	}

	/**
	 * 从配置获取ID生成器的工作者机器列表
	 * 
	 * @return
	 */
	private List<String> getWorkerList() {

		String wokers = env.getProperty("gid.workers");
		return CollectionUtil.splitStr2List(wokers, ",", true);
	}

	/**
	 * 从配置获取数据中心标识
	 * 
	 * @return
	 */
	private long getDataCenterId() {

		String datacenter = env.getProperty("gid.datacenter");
		return Long.parseLong(datacenter);
	}

	/**
	 * 获取本机的工作者ID
	 * 
	 * @return
	 */
	private long getWorkerId() {

		long workerId = -1L;
		List<String> workerList = getWorkerList();
		Set<String> localhostAddrSet = getLocalhostAddr();
		if (workerList != null) {
			for (int index = 0; index < workerList.size(); index++) {
				if (localhostAddrSet.contains(workerList.get(index))) {
					workerId = index;
				}
			}
		}
		return workerId;

	}

	/**
	 * 获取本机的多网卡网络地址
	 * 
	 * @return
	 */
	private Set<String> getLocalhostAddr() {

		Set<String> addrSet = new HashSet<String>();
		Enumeration<NetworkInterface> networkInterfaces = null;
		try {
			networkInterfaces = NetworkInterface.getNetworkInterfaces();
		} catch (SocketException e) {
			return addrSet;
		}
		while (networkInterfaces.hasMoreElements()) {
			NetworkInterface networkInterface = networkInterfaces.nextElement();
			if ("lo".equals(networkInterface.getName()))
				continue;
			Enumeration<InetAddress> inetAddressArray = networkInterface.getInetAddresses();
			while (inetAddressArray.hasMoreElements()) {
				InetAddress inetAddressEle = inetAddressArray.nextElement();
				addrSet.add(InetAddresses.toAddrString(inetAddressEle));
			}
		}
		return addrSet;
	}

	public static void main(String[] args) {

		IdWorker idGenerator = new IdWorker();

		for (int i = 0; i < (1 << 12); i++) {
			System.out.println(idGenerator.nextId());
		}

	}
}
