/***************************2.1: ACK/NACK
**************************** Feng Hong; 2015-12-09*/

package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.client.UDT_RetransTask;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.*;
import com.ouc.tcp.tool.TCP_TOOL;

import java.util.Vector;

public class TCP_Sender extends TCP_Sender_ADT {
	
	private Vector<TCP_PACKET> window = new Vector<>(); // 窗口缓冲区
	private int windowSize = 14; // 窗口大小
	private UDT_Timer timer;	// 定时器
	
	/*构造函数*/
	public TCP_Sender() {
		super();	//调用超类构造函数
		super.initTCP_Sender(this);		//初始化TCP发送端
	}
	
	@Override
	//可靠发送（应用层调用）：封装应用层数据，产生TCP数据报；需要修改
	public void rdt_send(int dataIndex, int[] appData) {
		
		// 检查窗口是否已满
		while (window.size() >= windowSize) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		//生成TCP数据报（设置序号和数据字段/校验和),注意打包的顺序
		TCP_HEADER header = new TCP_HEADER();
		header.setTh_seq(dataIndex * appData.length + 1);
		TCP_SEGMENT segment = new TCP_SEGMENT();
		segment.setData(appData);
		TCP_PACKET tcpPack = new TCP_PACKET(header, segment, destinAddr);		
		//更新带有checksum的TCP 报文头		
		header.setTh_sum(CheckSum.computeChkSum(tcpPack));
		
		// 放入窗口
		window.add(tcpPack);

		//发送TCP数据报
		udt_send(tcpPack);

		// 如果是窗口中的第一个包，启动定时器
		if (window.size() == 1) {
			if (timer != null) timer.cancel();
			timer = new UDT_Timer();
			GBNRetransTask task = new GBNRetransTask(client, window);
			timer.schedule(task, 1000, 1000);
		}

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
	// GBN 中不再使用此方法阻塞，逻辑已移至 rdt_send 的窗口检查
	public void waitACK() {
	}

	@Override
	//接收到ACK报文：检查校验和，将确认号插入ack队列;不需要修改
	public void recv(TCP_PACKET recvPack) {
		if (CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {
			int currentAck = recvPack.getTcpH().getTh_ack();
			System.out.println("Receive ACK Number： "+ currentAck);
			
			synchronized(window) {
				// 累积确认：移除窗口中序号小于等于 currentAck 的所有包
				int i = 0;
				while (i < window.size()) {
					if (window.get(i).getTcpH().getTh_seq() <= currentAck) {
						window.remove(i);
					} else {
						break; // 窗口是有序的，后续的包序号肯定更大
					}
				}

				// 如果窗口中还有包，重启定时器
				if (timer != null) {
					timer.cancel();
				}
				if (!window.isEmpty()) {
					timer = new UDT_Timer();
					GBNRetransTask task = new GBNRetransTask(client, window);
					timer.schedule(task, 1000, 1000);
				}
			}
		} else {
			System.out.println("Receive Corrupted ACK.");
		}
	}

	// GBN 重传任务：重传整个窗口
	class GBNRetransTask extends java.util.TimerTask {
		private com.ouc.tcp.client.Client client;
		private Vector<TCP_PACKET> window;

		public GBNRetransTask(com.ouc.tcp.client.Client client, Vector<TCP_PACKET> window) {
			this.client = client;
			this.window = window;
		}

		@Override
		public void run() {
			synchronized(window) {
				System.out.println("Timeout! Retransmit window, size: " + window.size());
				for (TCP_PACKET pack : window) {
					// 必须重新计算校验和，或者直接发送（如果原包已计算过且未被修改）
					// 这里为了稳妥，确保 eflag 正确
					pack.getTcpH().setTh_eflag((byte)4);
					client.send(pack);
				}
			}
		}
	}
	
}
