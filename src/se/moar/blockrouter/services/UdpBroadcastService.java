package se.moar.blockrouter.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;

import se.moar.blockrouter.packages.UthinBlock;
import se.moar.blockrouter.test.BitcoinPeerClient;
import se.moar.blockrouter.test.MempoolService;

/**
 * Servlet implementation class UdpBroadcastService
 */
@WebServlet(urlPatterns={}, loadOnStartup=15)
public class UdpBroadcastService extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static List<InetAddress> receivers = new ArrayList<InetAddress>();
	private static byte[] buffer = new byte[65507];
	private static DatagramSocket sendSocket;
	private static DatagramSocket receiveSocket;
	
	private static byte pingHeader = 21;
	private static byte pingHeaderReply = 22;
	private static byte uThinHeader = 24;
	private static byte txReqHeader = 27;
	
	private static long ms1min = 60 * 1000;
	
	private static boolean listenForPackets = true;
	
	private ScheduledExecutorService pingPeers = Executors.newSingleThreadScheduledExecutor();
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public UdpBroadcastService() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see Servlet#init(ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
		System.out.println("Starting network peer service");
		try {
			sendSocket = new DatagramSocket();
			
			Thread t = new Thread(new UdpListener());
	        t.start();

			long waitTime1min = ms1min - (System.currentTimeMillis() % ms1min);
			pingPeers.scheduleAtFixedRate(new Runnable() {
				  @Override
				  public void run() {
					  sendPing();
				  }
				}, waitTime1min, ms1min, TimeUnit.MILLISECONDS);
			
		} catch (SocketException e) {
			e.printStackTrace();
		}
		
	}

	/**
	 * @see Servlet#destroy()
	 */
	public void destroy() {
		sendSocket.close();
		listenForPackets = false;
	}
	
	public static void addReceiver(String ip) {
		try {
			InetAddress inetAddr = InetAddress.getByName(ip);
			receivers.add(inetAddr);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}
	
	public static void sendPing() {
		byte[] data = new byte[16];
			
		data[0] = pingHeader;
		
		if (receivers.size() > 0) {
			System.out.println("Sending ping to all peers");
			sendData(data);			
		} else {
			System.out.println("No peers to ping");
		}
			
	}
	
	public static void sendUthinBlock(UthinBlock uThin) {
		byte[] uThinSerialized;
		try {
			uThinSerialized = uThin.serializeUthinBlock();
			byte[] data = new byte[uThinSerialized.length+1];
			
			data[0] = uThinHeader;
			for (int i=1; i<data.length; i++) {
				data[i] = uThinSerialized[i-1];
			}
			
			sendData(data);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void sendData(byte[] data) {
		
			for (InetAddress receiver : receivers) {
				try {
		        	DatagramPacket packet = new DatagramPacket(data, data.length, receiver, 7400);
		        	sendSocket.send(packet);
					System.out.println("Sent UDP packet");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
	}
	
	private static class UdpListener implements Runnable {
	    public void run() {
	    	try {
				receiveSocket = new DatagramSocket(7400);
			} catch (SocketException e1) {
				e1.printStackTrace();
			}
	    	while (listenForPackets) {
	    		try {
					
					DatagramPacket packet = new DatagramPacket(buffer,buffer.length);
					receiveSocket.receive(packet);
					
					InetAddress address = packet.getAddress();
					
					byte[] receivedData = packet.getData();
					
					if (receivedData[0] == pingHeader) {
						System.out.println("Got ping");
						ByteArrayOutputStream pingReply = new ByteArrayOutputStream();
						DataOutputStream DataOutputStream = new DataOutputStream(pingReply);
						
						DataOutputStream.writeByte(pingHeaderReply);
						
						int height = BitcoinPeerClient.getBlockChainHeight();
						long mempoolSize = MempoolService.getSize();
						
						DataOutputStream.writeInt(height);
						DataOutputStream.writeLong(mempoolSize);
						DataOutputStream.close();
						DataOutputStream.flush();
						
						byte[] replyData = pingReply.toByteArray();
						
						DatagramPacket replyPacket = new DatagramPacket(replyData, replyData.length, address, 7400);
						receiveSocket.send(replyPacket);
					} else if (receivedData[0] == pingHeaderReply) {
						System.out.println("Got ping reply from " + address.getHostAddress());
						
						ByteArrayInputStream input = new ByteArrayInputStream(packet.getData());
						
						input.read();
						
						int height = readInt(input);
						long mempoolSize = readLong(input);
						
						System.out.println(address.getHostAddress() + " is on height " + height + " and has a mempool size of " + mempoolSize);
					} else if (receivedData[0] == uThinHeader) {
						ByteArrayInputStream input = new ByteArrayInputStream(packet.getData());
						
						input.read();
						
						byte[] uThinBlockRawData = new byte[receivedData.length-1];
						
						for (int i=0; i<uThinBlockRawData.length; i++) {
							uThinBlockRawData[i] = (byte) input.read();
						}
						
						try {
							UthinBlock uThin = new UthinBlock(uThinBlockRawData);	
							System.out.println(new Timestamp(System.currentTimeMillis()) + ": Got uThin block from " + address.getHostAddress() + " length " + packet.getLength() + " block hash " + uThin.getBlockHash().toString());
							BitcoinPeerClient.processUthin(uThin);
							
						} catch (Exception e) {
							e.printStackTrace();
						}
						
					}
					
				} catch (SocketException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
	    	}
	    	receiveSocket.close();
	    }
	}
	
	private static int readInt(InputStream input) throws IOException {
		byte[] intAsBytes = new byte[Integer.BYTES];
		for (int i=0; i<Integer.BYTES; i++) {
			intAsBytes[i] = (byte) input.read();
		}
		
		ByteBuffer wrapped = ByteBuffer.wrap(intAsBytes);
		
		return wrapped.getInt();
	}
	
	private static long readLong(InputStream input) throws IOException {
		byte[] longAsBytes = new byte[Long.BYTES];
		for (int i=0; i<Long.BYTES; i++) {
			longAsBytes[i] = (byte) input.read();
		}
		
		ByteBuffer wrapped = ByteBuffer.wrap(longAsBytes);
		
		return wrapped.getLong();
	}

}
