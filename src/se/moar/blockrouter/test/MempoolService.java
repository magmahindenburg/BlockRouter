package se.moar.blockrouter.test;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;

/**
 * Servlet implementation class MempoolService
 */
@WebServlet(urlPatterns={}, loadOnStartup=1)
public class MempoolService extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static HashMap<Sha256Hash, Transaction> mempoolMap = new HashMap<Sha256Hash, Transaction>(200000);
	private static HashMap<String, LinkedList<Sha256Hash>> shortTxHashIndex = new HashMap<String, LinkedList<Sha256Hash>>(200000);
	private static HashMap<Sha256Hash, Long> txAge = new HashMap<Sha256Hash, Long>(200000);
	private ScheduledExecutorService purgeMemPoolJob = Executors.newSingleThreadScheduledExecutor();
	
	static long ms1hr = 60 * 60 * 1000;
	static long ms48hr = 48 * ms1hr;
	
	private static long size = 0;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public MempoolService() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see Servlet#init(ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
		
		long waitTime1hr = ms1hr - (System.currentTimeMillis() % ms1hr);
		
		System.out.println("Started mempool service");
		
		purgeMemPoolJob.scheduleAtFixedRate(new Runnable() {
			  @Override
			  public void run() {
				  clearMempool();
			  }
			}, waitTime1hr, ms1hr, TimeUnit.MILLISECONDS);
	}

	/**
	 * @see Servlet#destroy()
	 */
	public void destroy() {
		purgeMemPoolJob.shutdown();
	}
	
	public static HashMap<Sha256Hash, Transaction> getMempoolMap() {
		return mempoolMap;
	}
	
	public static HashMap<String, LinkedList<Sha256Hash>> getShortTxHashIndex() {
		return shortTxHashIndex;
	}
	
	public static void clearMempool() {
		
		long timeNow = System.currentTimeMillis();
		
		System.out.println(new Timestamp(System.currentTimeMillis()) + "Startung to clear mempool. Current size " + size);
		
		for (Sha256Hash txHash : txAge.keySet()) {
			long txTime = txAge.get(txHash);
			if ((timeNow - txTime) > ms48hr) {
				removeFromMempool(txHash);
			}
		}
		
		System.out.println(new Timestamp(System.currentTimeMillis()) + "Done clearing mempool. New size " + size);
		
	}
	
	public static void insertIntoMempool(Transaction tx) {
		
		if (!mempoolMap.containsKey(tx.getHash())) {
			mempoolMap.put(tx.getHash(), tx);
			
			String firstBytes = tx.getHashAsString().substring(0,4);
			if (shortTxHashIndex.containsKey(firstBytes)) {
				shortTxHashIndex.get(firstBytes).addLast(tx.getHash());
			} else {
				LinkedList<Sha256Hash> newList = new LinkedList<Sha256Hash>();
				newList.add(tx.getHash());
				shortTxHashIndex.put(firstBytes, newList);
			}
			txAge.put(tx.getHash(), System.currentTimeMillis());
			size++;
		}
	}
	
	public static long getSize() {
		return size;
	}
	
	public static void removeFromMempool(Transaction tx) {
		removeFromMempool(tx.getHash());
	}
	
	public static void removeFromMempool(Sha256Hash txHash) {
		if (mempoolMap.containsKey(txHash)) {
			mempoolMap.remove(txHash);
			
			String firstBytes = txHash.toString().substring(0,4);
			if (shortTxHashIndex.containsKey(firstBytes)) {
				shortTxHashIndex.get(firstBytes).remove(txHash);
			}
			
			txAge.remove(txHash);
			size--;
		}
	}
	
	public static void removeFromMempool(List<Transaction> txList) {
		int sizeBefore = mempoolMap.size();
		for (Transaction tx : txList) {
			removeFromMempool(tx);
		}
		int sizeAfter = mempoolMap.size();
		int change = sizeBefore - sizeAfter;
		System.out.println("Removed " + change + " transactions from mempool");
	}
	
	public static void loadMempoolFromBitcoind() {
		Thread t = new Thread(new loadMempoolThread());
        t.start();
	}

	private static class loadMempoolThread implements Runnable {
	    public void run() {
	    	System.out.println("Loading transactions from bitcoind...");
	    	RPCclient rpcClient = new RPCclient();
			List<Transaction> mempoolListFromBitcoind = rpcClient.getRawMempool();
			
			for (Transaction tx : mempoolListFromBitcoind) {
				insertIntoMempool(tx);
			}
			System.out.println("Inserted " + mempoolListFromBitcoind.size() + " transactions into mempool");
	    }
	}

}
