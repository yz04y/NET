/***************************2.1: ACK/NACK*****************/
/***** Feng Hong; 2015-12-09******************************/
package com.ouc.tcp.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import com.ouc.tcp.client.TCP_Receiver_ADT;
import com.ouc.tcp.message.*;
import com.ouc.tcp.tool.TCP_TOOL;

public class TCP_Receiver extends TCP_Receiver_ADT {
	
	private TCP_PACKET ackPack;	//回复的ACK报文段
	int lastAckNum = 0; // GBN: 用于记录上一个成功接收并回复的确认号
	int expectedSeqNum = 1; // GBN: 期待接收的下一个包序号

	/*构造函数*/
	public TCP_Receiver() {
		super();	//调用超类构造函数
		super.initTCP_Receiver(this);	//初始化TCP接收端
	}

	@Override
	//接收到数据报：检查校验和，设置回复的ACK报文段
	public void rdt_recv(TCP_PACKET recvPack) {
		//检查校验码，生成ACK
		if(CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {
			int currentSeq = recvPack.getTcpH().getTh_seq();
			
			// GBN: 只有当收到期待的序号时才处理
			if (currentSeq == expectedSeqNum) {
				System.out.println("Receive Expected Packet: " + currentSeq);
				
				// 构造累积确认 ACK
				tcpH.setTh_ack(currentSeq);
				ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
				tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
				ackPack.setTcpH(tcpH);
				reply(ackPack);			
				
				// 更新状态
				lastAckNum = currentSeq;
				expectedSeqNum += recvPack.getTcpS().getData().length;

				// 交付数据
				dataQueue.add(recvPack.getTcpS().getData());				
			} else {
				// 收到失序包（Out-of-order）或重复包
				System.out.println("Receive Out-of-order/Duplicate Packet: " + currentSeq + ", expect: " + expectedSeqNum);
				
				// 重新发送最后一个累积 ACK
				tcpH.setTh_ack(lastAckNum);
				ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
				tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
				ackPack.setTcpH(tcpH);
				reply(ackPack);
			}
		} else {
			System.out.println("Corrupted packet, send redundant ACK for " + lastAckNum);
			
			// 包损坏，发送上一个正确接收包的累计 ACK
			tcpH.setTh_ack(lastAckNum);
			ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
			tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
			ackPack.setTcpH(tcpH);
			reply(ackPack);
		}
		
		System.out.println();
		
		//交付数据（每20组数据交付一次）
		if(dataQueue.size() >= 20) 
			deliver_data();	
	}

	@Override
	//交付数据（将数据写入文件）；不需要修改
	public void deliver_data() {
		//检查dataQueue，将数据写入文件
		File fw = new File("recvData.txt");
		BufferedWriter writer;
		
		try {
			writer = new BufferedWriter(new FileWriter(fw, true));
			
			//循环检查data队列中是否有新交付数据
			while(!dataQueue.isEmpty()) {
				int[] data = dataQueue.poll();
				
				//将数据写入文件
				for(int i = 0; i < data.length; i++) {
					writer.write(data[i] + "\n");
				}
				
				writer.flush();		//清空输出缓存
			}
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	//回复ACK报文段
	public void reply(TCP_PACKET replyPack) {
		//设置错误控制标志
		replyPack.getTcpH().setTh_eflag((byte)4);	//eFlag=0，信道无错误
				
		//发送数据报
		client.send(replyPack);
	}
	
}
