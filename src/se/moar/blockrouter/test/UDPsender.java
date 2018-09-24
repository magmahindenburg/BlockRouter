package se.moar.blockrouter.test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

public class UDPsender {
	
	private List<InetAddress> receivers = new ArrayList<InetAddress>();
	private byte[] buffer = new byte[65507];
	
	public UDPsender() {
		
	}
	
	public void addReceiver(InetAddress receiver) {
		receivers.add(receiver);
	}
	
	public void sendData(byte[] data) {
		
		DatagramSocket socket;
		try {
			socket = new DatagramSocket();
			for (InetAddress receiver : receivers) {
				try {
		        	DatagramPacket packet = new DatagramPacket(data, data.length, receiver, 7555);
					socket.send(packet);
					System.out.println("Sent UDP packet");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			socket.close();
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
	}

}
