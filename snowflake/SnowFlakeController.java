package cn.com.ut.biz.goods;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping(value = "/snowflake")
public class SnowFlakeController {


	@PostMapping(value = "/snow")
	@ServiceComponent(session = false)
	public void snow() {

		// long startU = System.currentTimeMillis();
		// for (int i = 0; i < 100000000; i++) {
		// String id = CommonUtil.getUUID();
		// long end = System.currentTimeMillis();
		// // System.out.println("UUID已耗时：" + (end - startU));
		// if ((end - startU) >= 1000) {
		// System.out.println("UUID跳出循环，当前索引为: " + i);
		// System.out.println("UUID为: " + id);
		// System.out.println("UUID位数: " + String.valueOf(id).length());
		// break;
		// }
		// }

		SnowflakeIdFactory snowflake = new SnowflakeIdFactory();
		long start = System.currentTimeMillis();
		for (int i = 0; i < 100000000; i++) {
			// String id = snowflake.createOrderNo("1664");
			String id = snowflake.nextId();
			long end = System.currentTimeMillis();
			// System.out.println("id为: " + id);
			// System.out.println("已耗时：" + (end - start));
			if ((end - start) >= 1000) {
				System.out.println("已耗时: " + (end - start));
				System.out.println("跳出循环，当前索引为: " + i);
				System.out.println("id为: " + id);
				System.out.println("id位数: " + id.length());
				break;
			}
		}

		// long start = System.currentTimeMillis();
		// for (int i = 0; i < 100000000; i++) {
		// long id = snowflakeWorker.nextIdOld();
		// long end = System.currentTimeMillis();
		// // System.out.println("已耗时：" + (end - start));
		// if ((end - start) >= 1000) {
		// System.out.println("跳出循环，当前索引为: " + i);
		// System.out.println("id为: " + id);
		// System.out.println("id位数: " + String.valueOf(id).length());
		// // System.out.println("id二进制为: " + Long.toBinaryString(id));
		// System.out.println("Redis新索引为: " +
		// redisHelper.get("orderNum").toString());
		// break;
		// }
		// // System.out.println("id为: " + Long.toBinaryString(id));
		// // System.out.println("id为: " + id);
		// // System.out.println("id位数: " + String.valueOf(id).length());
		// }
	}
}
