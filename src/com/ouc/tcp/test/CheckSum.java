package com.ouc.tcp.test;

import java.util.zip.CRC32;

import com.ouc.tcp.message.TCP_HEADER;
import com.ouc.tcp.message.TCP_PACKET;

public class CheckSum {
	
	/*计算TCP报文段校验和：只需校验TCP首部中的seq、ack和sum，以及TCP数据字段*/
	public static short computeChkSum(TCP_PACKET tcpPack) {
		int checkSum = 0;
		TCP_HEADER header = tcpPack.getTcpH();
		int[] data = tcpPack.getTcpS().getData();
		int sum = 0;

		// 校验seq
		int seq = header.getTh_seq();
		checkSum += (seq >> 16) & 0xFFFF;
		checkSum += seq & 0xFFFF;

		// 校验ack
		int ack = header.getTh_ack();
		checkSum += (ack >> 16) & 0xFFFF;
		checkSum += ack & 0xFFFF;

		// 校验sum (计算校验和时，sum字段本身必须视为0)
		checkSum += 0;
		checkSum += 0;

		// 校验数据字段
		if (data != null) {
			for (int i = 0; i < data.length; i++) {
				checkSum += (data[i] >> 16) & 0xFFFF;
				checkSum += data[i] & 0xFFFF;
			}
		}

		// 处理进位
		while ((checkSum >> 16) != 0) {
			checkSum = (checkSum & 0xFFFF) + (checkSum >> 16);
		}

		return (short) (~checkSum & 0xFFFF);
	}
	
}
