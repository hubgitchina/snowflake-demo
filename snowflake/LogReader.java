package cn.com.ut.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.com.ut.core.common.util.CommonUtil;
import cn.com.ut.core.common.util.ExceptionUtil;

public class LogReader implements Runnable {

	private File logFile = null;
	private long lastTimeFileSize = 0; // 上次文件大小
	private int lastLineNumber = 0; // 上次读到行数
	private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	public LogReader(File logFile) {
		this.logFile = logFile;
		lastTimeFileSize = logFile.length();
		RandomAccessFile randomFile = null;
		try {
			randomFile = new RandomAccessFile(logFile, "r");
			randomFile.seek(0);
			String tmp = null;
			while ((tmp = randomFile.readLine()) != null) {
				lastLineNumber++;
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (randomFile != null) {
				try {
					randomFile.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * 实时输出日志信息
	 */
	public void run() {

		while (true) {
			RandomAccessFile randomFile = null;
			try {
				randomFile = new RandomAccessFile(logFile, "r");
				randomFile.seek(lastTimeFileSize);
				String tmp = null;
				Pattern p = Pattern.compile(".*?ERROR.*?");
				boolean isConsole = false;
				String errorContent = "";
				// System.out.println("lastLineNumber：" + lastLineNumber);
				// System.out.println("lastTimeFileSize：" + lastTimeFileSize);
				while ((tmp = randomFile.readLine()) != null) {
					lastLineNumber++;
					Matcher m = p.matcher(tmp);
					if (m.find()) {
						isConsole = true;
						System.out.println("发现异常，在【 " + lastLineNumber + " 】行");
						System.out.println(new String(tmp.getBytes("ISO8859-1"), "utf-8"));
						errorContent = new String(tmp.getBytes("ISO8859-1"), "utf-8");
						continue;
					}
					if (isConsole) {
						System.out.println(new String(tmp.getBytes("ISO8859-1"), "utf-8"));
						errorContent = errorContent + "<br />"
								+ new String(tmp.getBytes("ISO8859-1"), "utf-8");
					}
				}
				if (CommonUtil.isNotEmpty(errorContent)) {
					try {
						JavaMail.sendMail("wangpeng1@ut.cn", "商业服务平台异常报告", errorContent);
					} catch (Exception e) {
						ExceptionUtil.throwServiceException("发送报警邮件失败");
					}
				}

				lastTimeFileSize = randomFile.length();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (randomFile != null) {
					try {
						randomFile.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) {

		File logFile = new File("D:\\cs-pc-logs\\cs-pc-commerce.log");
		Thread rthread = new Thread(new LogReader(logFile));
		rthread.start();
	}
}
