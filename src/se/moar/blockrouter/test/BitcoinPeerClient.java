package se.moar.blockrouter.test;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.GetDataMessage;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.listeners.BlocksDownloadedEventListener;
import org.bitcoinj.core.listeners.OnTransactionBroadcastListener;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.core.listeners.PeerDataEventListener;
import org.bitcoinj.core.listeners.PeerDisconnectedEventListener;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;

import se.moar.blockrouter.packages.UthinBlock;
import se.moar.blockrouter.services.UdpBroadcastService;

/**
 * Servlet implementation class BitcoinPeerClient
 */
@WebServlet(urlPatterns={}, loadOnStartup=10)
public class BitcoinPeerClient extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static PeerGroup peerGroup = null;
	private static SPVBlockStore blockStore = null;
	private static BlockChain chain = null;
	private static NetworkParameters params = MainNetParams.get();
	private static long blockHeight = 0;
	private static long ms1min = 60 * 1000;
	private ScheduledExecutorService checkBitcoinPeers = Executors.newSingleThreadScheduledExecutor();
	public static Context context = new Context(MainNetParams.get());
	private static boolean firstTxReceived = false;
	
	private static ConcurrentLinkedQueue<UthinBlock> uthinBlockQueue = new ConcurrentLinkedQueue<UthinBlock>();
	private static ConcurrentLinkedQueue<UthinBlock> pendingUthinTxQueue = new ConcurrentLinkedQueue<UthinBlock>();
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public BitcoinPeerClient() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see Servlet#init(ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
		System.out.println("Starting Blockchain servlet");
		
		try {
			blockStore = new SPVBlockStore(params, new File("/var/blockchain/spv"));
			blockStore.getChainHead();
			// InputStream checkpointsInputStream = new FileInputStream(("/var/blockchain/checkpoints.txt"));
			// CheckpointManager.checkpoint(MainNetParams.get(), checkpointsInputStream, blockStore, System.currentTimeMillis());

			chain = new BlockChain(params, blockStore);
			peerGroup = new PeerGroup(params, chain);
			
			if (blockStore.getChainHead().getHeight() > blockHeight) {
				blockHeight = blockStore.getChainHead().getHeight();
			}
			
			peerGroup.setUserAgent("BitcoinJ", "0.15.0");
			peerGroup.startAsync();
			peerGroup.downloadBlockChain();
			
			peerGroup.startBlockChainDownload(new PeerDataEventListener() {
				
				@Override
				public Message onPreMessageReceived(Peer arg0, Message arg1) {
					// TODO Auto-generated method stub
					return null;
				}
				
				@Override
				public List<Message> getData(Peer arg0, GetDataMessage arg1) {
					// TODO Auto-generated method stub
					return null;
				}
				
				@Override
				public void onChainDownloadStarted(Peer arg0, int arg1) {
					System.out.println("Started blockchain download");
					
				}
				
				@Override
				public void onBlocksDownloaded(Peer peer, Block block, FilteredBlock arg2, int arg3) {
					
				}
			});
			
			peerGroup.addConnectedEventListener(new PeerConnectedEventListener() {
				
				@Override
				public void onPeerConnected(Peer peer, int arg1) {
					System.out.println("Connected to peer " + peer.getAddress().toString());
					
				}
			});
			
			peerGroup.addDisconnectedEventListener(new PeerDisconnectedEventListener() {
				
				@Override
				public void onPeerDisconnected(Peer peer, int arg1) {
					System.out.println("Disconnected from peer " + peer.getAddress().toString());
				}
			});
			
			peerGroup.addOnTransactionBroadcastListener(new OnTransactionBroadcastListener() {
				
				@Override
				public void onTransaction(Peer arg0, Transaction tx) {
					// System.out.println("Got transaction " + tx.getHashAsString());
					MempoolService.insertIntoMempool(tx);
					
					if (!firstTxReceived) {
						System.out.println("Received first tx from bitcoind");
						firstTxReceived = true;
						MempoolService.loadMempoolFromBitcoind();
					}
				}
			});
			
			
			
			peerGroup.addBlocksDownloadedEventListener(new BlocksDownloadedEventListener() {
				
				@Override
				public void onBlocksDownloaded(Peer peer, Block block, FilteredBlock arg2, int arg3) {
					System.out.println("Got block " + block.getHashAsString() + " from" + peer.getAddress().getAddr().toString() + " and and int " + arg3 );
					/*
					System.out.println("Saving block to disk");
					try {
						saveBlockToDisk(block);
					} catch (IOException e) {
						e.printStackTrace();
					}
					*/
					
					if (arg3 == 0) {
						long originalSize = block.bitcoinSerialize().length;
						UthinBlock uThinBlock = new UthinBlock(block);
						
						try {
							byte[] uThinBytes = uThinBlock.serializeUthinBlock();
							
							long uThinSize = uThinBytes.length;
							
							if (uThinSize < 65507) {
								UdpBroadcastService.sendUthinBlock(uThinBlock);								
							} else {
								System.out.println("uThin too big for UDP");
							}
							
							double compression = ((double) originalSize - (double) uThinSize) / (double) originalSize;
							compression = compression * 100;
							
							System.out.println("Created a uThin block with size " + uThinSize + " at " + new BigDecimal(compression).setScale(2, RoundingMode.HALF_UP).toPlainString() + "% compression");
							
							// UthinBlock uThinBlock2 = new UthinBlock(uThinBytes);
							
							// rebuildUthin(uThinBlock2);
							
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					MempoolService.removeFromMempool(block.getTransactions());
				}
			});
			
			long waitTime1min = ms1min - (System.currentTimeMillis() % ms1min);
			
			checkBitcoinPeers.scheduleAtFixedRate(new Runnable() {
				  @Override
				  public void run() {
					  checkPeers();
				  }
				}, waitTime1min, ms1min, TimeUnit.MILLISECONDS);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void saveBlockToDisk(Block block) throws IOException {
		Path path = Paths.get("/home/magma/dev/blocks/" +  block.getHashAsString() + ".dat");
		Files.write(path, block.bitcoinSerialize());
	}
	
	public static int getBlockChainHeight() {
		return chain.getBestChainHeight();
	}
	
	public static void processUthin(UthinBlock uThin) {
		
		// Rebuilt the uThin into a normal block
		try {
			long beforeTime = System.currentTimeMillis();
			Block rebuiltUThin = uThin.getFullBlock(MempoolService.getMempoolMap(), MempoolService.getShortTxHashIndex());
			long afterTime = System.currentTimeMillis();
			rebuiltUThin.bitcoinSerialize();
			long rebuildTime = afterTime - beforeTime;
			System.out.println("Rebuilt block from uThin in " + rebuildTime + " ms");
		} catch (MissingTransactionException e) {
			System.out.println("Missing " + e.getMissingTransactionPositions().size() + " transactions, sending request for these.");
			
			// Send request for missing tx
			
			/*
			for (short pos : e.getMissingTransactionPositions()) {
				System.out.println("Missing tx at position " + pos);
			}
			*/
				
		} catch (Exception e) {
			System.out.println("Could not rebuild uThin");
			e.printStackTrace();
		}
	}
	
	private static void rebuildUthin(UthinBlock uThin) {
		try {
			long beforeTime = System.currentTimeMillis();
			Block rebuiltUThin = uThin.getFullBlock(MempoolService.getMempoolMap(), MempoolService.getShortTxHashIndex());
			long afterTime = System.currentTimeMillis();
			rebuiltUThin.bitcoinSerialize();
			long rebuildTime = afterTime - beforeTime;
			System.out.println("Rebuilt block from uThin in " + rebuildTime + " ms");
		} catch (MissingTransactionException e) {
			System.out.println("Missing " + e.getMissingTransactionPositions().size() + " transactions");
			/*
			for (short pos : e.getMissingTransactionPositions()) {
				System.out.println("Missing tx at position " + pos);
			}
			*/
				
		} catch (Exception e) {
			System.out.println("Could not rebuild uThin");
			e.printStackTrace();
		}
	}
	
	private void checkPeers() {
		if (peerGroup != null) {
			System.out.println("Connected to " + peerGroup.getConnectedPeers().size() + " peers");
		}
		System.out.println("Mempool size: " + MempoolService.getSize());
		if (blockStore != null) {
			try {
				System.out.println("Blockchain on height " + blockStore.getChainHead().getHeight());
			} catch (BlockStoreException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * @see Servlet#destroy()
	 */
	public void destroy() {
		try {
			blockStore.close();
		} catch (BlockStoreException e) {
			e.printStackTrace();
		}
		// peerGroup.stopAsync();
		for (Peer peer : peerGroup.getConnectedPeers()) {
			peer.close();
		}
		peerGroup.stopAsync();
		peerGroup.stop();
		
	}
	
}
