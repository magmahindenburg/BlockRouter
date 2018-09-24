package se.moar.blockrouter.test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;

import se.moar.blockrouter.packages.UthinBlock;

public class BlockTester {
	
	private static HashMap<Sha256Hash, Transaction> mempoolMap = new HashMap<Sha256Hash, Transaction>(100000);
	private static HashMap<String, LinkedList<Sha256Hash>> shortTxHashIndex = new HashMap<String, LinkedList<Sha256Hash>>(100000);
	public static Context context = new Context(MainNetParams.get());

	public static void main(String[] args) {
		
		Block block1 = new Block(MainNetParams.get(), readBlockFile("449037"));
		Block block2 = new Block(MainNetParams.get(), readBlockFile("449277"));
		Block block3 = new Block(MainNetParams.get(), readBlockFile("449278"));
		Block block4 = new Block(MainNetParams.get(), readBlockFile("449589"));
		
		System.out.println("Block has " + block1.getTransactions().size() + " tx");
		
		System.out.println(new String(block2.getTransactions().get(0).getInputs().get(0).getScriptBytes()));
		System.out.println(new String(block2.getTransactions().get(0).getInputs().get(0).bitcoinSerialize()));
		

		for (Transaction tx : block1.getTransactions()) {
			if (!block1.getTransactions().get(0).getHash().equals(tx.getHash())) {
				mempoolMap.put(tx.getHash(), tx);
				String firstBytes = tx.getHashAsString().substring(0,4);
				if (shortTxHashIndex.containsKey(firstBytes)) {
					shortTxHashIndex.get(firstBytes).addLast(tx.getHash());
				} else {
					LinkedList<Sha256Hash> newList = new LinkedList<Sha256Hash>();
					newList.add(tx.getHash());
					shortTxHashIndex.put(firstBytes, newList);
				}
			}
		}
		for (Transaction tx : block2.getTransactions()) {
			if (!block2.getTransactions().get(0).getHash().equals(tx.getHash())) {
				mempoolMap.put(tx.getHash(), tx);	
				String firstBytes = tx.getHashAsString().substring(0,4);
				if (shortTxHashIndex.containsKey(firstBytes)) {
					shortTxHashIndex.get(firstBytes).addLast(tx.getHash());
				} else {
					LinkedList<Sha256Hash> newList = new LinkedList<Sha256Hash>();
					newList.add(tx.getHash());
					shortTxHashIndex.put(firstBytes, newList);
				}
			}
		}
		
		for (Transaction tx : block3.getTransactions()) {
			if (!block3.getTransactions().get(0).getHash().equals(tx.getHash())) {
				mempoolMap.put(tx.getHash(), tx);	
				String firstBytes = tx.getHashAsString().substring(0,4);
				if (shortTxHashIndex.containsKey(firstBytes)) {
					shortTxHashIndex.get(firstBytes).addLast(tx.getHash());
				} else {
					LinkedList<Sha256Hash> newList = new LinkedList<Sha256Hash>();
					newList.add(tx.getHash());
					shortTxHashIndex.put(firstBytes, newList);
				}
			}
		}
		
		for (Transaction tx : block4.getTransactions()) {
			if (!block4.getTransactions().get(0).getHash().equals(tx.getHash())) {
				mempoolMap.put(tx.getHash(), tx);
				String firstBytes = tx.getHashAsString().substring(0,4);
				if (shortTxHashIndex.containsKey(firstBytes)) {
					shortTxHashIndex.get(firstBytes).addLast(tx.getHash());
				} else {
					LinkedList<Sha256Hash> newList = new LinkedList<Sha256Hash>();
					newList.add(tx.getHash());
					shortTxHashIndex.put(firstBytes, newList);
				}
			}
		}
		
		// System.out.println("removing " + block1.getTransactions().get(3).getHash());
		// mempoolMap.remove(block1.getTransactions().get(3).getHash());
		// mempoolMap.remove(block1.getTransactions().get(17).getHash());
		
		testBlock(block1);
		testBlock(block2);
		testBlock(block3);
		testBlock(block4);
		
	}
	
	private static void testBlock(Block block) {
		System.out.println("Block hash of serialized bytes: " + Sha256Hash.create(block.bitcoinSerialize()));
		
		System.out.println(block.getHashAsString());
		
		try {
			long timeBeforeSerialization = System.currentTimeMillis();
			UthinBlock uThinBlock = new UthinBlock(block);
			byte[] serializedUthin = uThinBlock.serializeUthinBlock();
			long timeAfterSerialization = System.currentTimeMillis();
			
			long serializationTimeNano = timeAfterSerialization - timeBeforeSerialization;
			
			System.out.println("Serialized uThin in " + serializationTimeNano + " ms");
			
			System.out.println("Size of uThin block: " + serializedUthin.length);
			System.out.println("Mempool size: " + mempoolMap.size());

			long timeBeforeRebuild = System.nanoTime();
			UthinBlock uthinBlock2 = new UthinBlock(serializedUthin);
			Block fullBlock = uthinBlock2.getFullBlock(mempoolMap, shortTxHashIndex);
			long timeAfterRebuild = System.nanoTime();
			
			double rebuildTime = timeAfterRebuild - timeBeforeRebuild;
			rebuildTime = rebuildTime / 1000 / 1000;
			System.out.println("rebuilt uThin in " + rebuildTime + " ms");
			
			System.out.println("New full block size: " + fullBlock.bitcoinSerialize().length);
			
			fullBlock.verifyHeader();
			
			System.out.println(fullBlock.getHashAsString());
			System.out.println("Block transactions: " + fullBlock.getTransactions().size());
			System.out.println("Block hash of serialized bytes: " + Sha256Hash.create(fullBlock.bitcoinSerialize()).toString());
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (MissingTransactionException e) {
			
			for (short txPos : e.getMissingTransactionPositions()) {
				System.out.println("missing tx at position " + txPos);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("---");
	}
	
	
	private static byte[] readBlockFile(String blockHeight) {
		BufferedReader br = null;
		FileReader fr = null;
		String blockhexString = "";
		byte[] blockBytes = null;

		try {

			fr = new FileReader("/home/magma/dev/"+ blockHeight +".txt");
			br = new BufferedReader(fr);

			String sCurrentLine;

			br = new BufferedReader(new FileReader("/home/magma/dev/"+ blockHeight +".txt"));

			while ((sCurrentLine = br.readLine()) != null) {
				blockhexString = blockhexString + sCurrentLine;
			}
			
			blockBytes = HexStringHandler.hexStringToByteArray(blockhexString);

		} catch (IOException e) {

			e.printStackTrace();

		} finally {

			try {

				if (br != null)
					br.close();

				if (fr != null)
					fr.close();

			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		
		return blockBytes;
	}

}
