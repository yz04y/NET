/***************************2.1: ACK/NACK
**************************** Feng Hong; 2015-12-09*/

package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.client.UDT_RetransTask;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.*;
import com.ouc.tcp.tool.TCP_TOOL;

public class TCP_Sender extends TCP_Sender_ADT {
	
	private TCP_PACKET tcpPack;	//待发送的TCP数据报
	private volatile int flag = 0;
	private UDT_Timer timer;	// 定时器
	private UDT_RetransTask task;	// 重传任务
	
	/*构造函数*/
	public TCP_Sender() {
		super();	//调用超类构造函数
		super.initTCP_Sender(this);		//初始化TCP发送端
	}
	
	@Override
	//可靠发送（应用层调用）：封装应用层数据，产生TCP数据报；需要修改
	public void rdt_send(int dataIndex, int[] appData) {
		
		//生成TCP数据报（设置序号和数据字段/校验和),注意打包的顺序
		TCP_HEADER header = new TCP_HEADER();
		header.setTh_seq(dataIndex * appData.length + 1);
		TCP_SEGMENT segment = new TCP_SEGMENT();
		segment.setData(appData);
		tcpPack = new TCP_PACKET(header, segment, destinAddr);		
		//更新带有checksum的TCP 报文头		
		header.setTh_sum(CheckSum.computeChkSum(tcpPack));
		
		// 发送前先取消旧定时器，防止干扰
		if (timer != null) {
			timer.cancel();
		}
		
		//发送TCP数据报
		udt_send(tcpPack);
		flag = 0;

		// rdt3.0: 启动定时器（每隔1秒重传直到收到ACK）
		timer = new UDT_Timer();
		task = new UDT_RetransTask(client, tcpPack);
		timer.schedule(task, 1000, 1000);
		
		//等待ACK报文
		waitACK();

	}
	
	@Override
	//不可靠发送：将打包好的TCP数据报通过不可靠传输信道发送；仅需修改错误标志
	public void udt_send(TCP_PACKET stcpPack) {
		//设置错误控制标志
		stcpPack.getTcpH().setTh_eflag((byte)4);
		//发送数据报
		client.send(stcpPack);
	}
	
	@Override
	//需要修改
	public void waitACK() {
		//循环检查ackQueue
		//循环检查确认号对列中是否有新收到的ACK		
		while (flag == 0) {
			try {
				Thread.sleep(10); // 添加短暂休眠，避免忙等待占用CPU资源
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	@Override
	//接收到ACK报文：检查校验和，将确认号插入ack队列;不需要修改
	public void recv(TCP_PACKET recvPack) {
		if (CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {
			int currentAck = recvPack.getTcpH().getTh_ack();
			System.out.println("Receive ACK Number： "+ currentAck);
			
			if (tcpPack != null && currentAck == tcpPack.getTcpH().getTh_seq()) {
				// 收到正确且期待的ACK
				System.out.println("Clear: " + tcpPack.getTcpH().getTh_seq());
				// rdt3.0: 停止定时器
				if (timer != null) timer.cancel();
				flag = 1;
			} else {
				// 收到冗余ACK或不匹配的序号
				if (tcpPack != null) {
					System.out.println("Receive Unmatched ACK: " + currentAck + ", but expect: " + tcpPack.getTcpH().getTh_seq());
					// rdt3.0: 冗余ACK通常依赖超时，但也可以选择快速重传提高效率
					//udt_send(tcpPack);
				}
			}
		} else {
			System.out.println("Receive Corrupted ACK.");
			// ACK损坏，等超时重传
		}
	}
	
}
