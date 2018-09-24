package se.moar.blockrouter.packages;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.DatatypeConverter;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;

import com.github.mgunlogson.cuckoofilter4j.CuckooFilter;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import se.moar.blockrouter.test.MissingTransactionException;

public class UthinBlock {
	
	private Sha256Hash blockHash;
	private Sha256Hash merkleRoot;
	private Transaction coinbase;
	private Block reducedBlock;
	private boolean reduced = true;
	private BloomFilter<byte[]> transactionBloomFilter;
	private int transactionCount;
	
	private ArrayList<Short> transactionPositionList;
	private short[] transactionPositionArray;
	
	private ArrayList<String> transactionBytesList;
	private String[] transactionBytesArray;
	
	public UthinBlock(Block originalBlock) {
		Block newBlock = new Block(MainNetParams.get(), originalBlock.bitcoinSerialize());
		blockHash = newBlock.getHash();
		merkleRoot = newBlock.getMerkleRoot();
		
		// Set the bloom filter and transaction positions
		List<Sha256Hash> sortedTransactions = new ArrayList<Sha256Hash>();
		HashMap<Sha256Hash, Integer> txPositionMap = new HashMap<Sha256Hash, Integer>();
		
		transactionCount = newBlock.getTransactions().size();
		int bloomFilterSize = transactionCount;

		transactionBytesList = new ArrayList<String>();
		transactionBytesArray = new String[bloomFilterSize];
		
		transactionBloomFilter = BloomFilter.create(Funnels.byteArrayFunnel(), bloomFilterSize);
		
		int pos = 0;
		for (Transaction tx : newBlock.getTransactions()) {
			if (pos > 0) {
				transactionBloomFilter.put(tx.getHash().getBytes());	
			}
			txPositionMap.put(tx.getHash(), pos);
			sortedTransactions.add(tx.getHash());
			pos++;
		}
		
		Collections.sort(sortedTransactions);
		
		transactionPositionList = new ArrayList<Short>();
		transactionPositionArray = new short[newBlock.getTransactions().size()];
		
		int i=0;
		for (Sha256Hash txHash : sortedTransactions) {
			// Get tx position
			short position = txPositionMap.get(txHash).shortValue();
			transactionPositionList.add(position);
			transactionPositionArray[i] = position;
			
			transactionBytesList.add(txHash.toString().substring(0,4));
			transactionBytesArray[i] = txHash.toString().substring(0,4);
			
			i++;
		}
		
		coinbase = newBlock.getTransactions().get(0);
		
		List<Transaction> coinbaseTxList = new LinkedList<Transaction>();
		coinbaseTxList.add(coinbase);
		
		newBlock.replaceTransactionList(coinbaseTxList);
		reducedBlock = newBlock;
	}
	
	public UthinBlock(byte[] inputbytes) throws IOException {
		
		ByteArrayInputStream input = new ByteArrayInputStream(inputbytes);
		
		byte[] blockHashBytes = new byte[32];
		byte[] merkeleRootHashBytes = new byte[32];
		
		for (int i=0; i<32; i++) {
			blockHashBytes[i] = (byte) input.read();
		}
		
		blockHash = Sha256Hash.wrap(blockHashBytes);
		
		for (int i=0; i<32; i++) {
			merkeleRootHashBytes[i] = (byte) input.read();
		}
		
		merkleRoot = Sha256Hash.wrap(merkeleRootHashBytes);
		
		int reducedBlockLength = readInt(input);
		
		byte[] serializedReducedBlock = new byte[reducedBlockLength];
		
		for (int i=0; i<serializedReducedBlock.length; i++) {
			serializedReducedBlock[i] = (byte) input.read();
		}
		
		reducedBlock = new Block(MainNetParams.get(), serializedReducedBlock);
		
		// Get the transaction bloom filter
		int bloomFilterLength = readInt(input);
		byte[] serializedBloomFilter = new byte[bloomFilterLength];
		
		for (int i=0; i<serializedBloomFilter.length; i++) {
			serializedBloomFilter[i] = (byte) input.read();
		}
		
		transactionBloomFilter = BloomFilter.readFrom(new ByteArrayInputStream(serializedBloomFilter), Funnels.byteArrayFunnel());
		
		// Read position list
		int positionListLength = readInt(input);
		transactionCount = positionListLength;
		transactionPositionArray = new short[positionListLength];
		transactionPositionList = new ArrayList<Short>();
		
		for (int i=0; i<transactionPositionArray.length; i++) {
			short position = readShort(input);
			transactionPositionArray[i] = position;
			transactionPositionList.add(position);
		}
		
		// Read size list
		int txBytesListSize = readInt(input);
		
		transactionBytesList = new ArrayList<String>();
		transactionBytesArray = new String[txBytesListSize];
		
		for (int i=0; i<transactionBytesArray.length; i++) {
			byte firstByte = (byte) input.read();
			byte secondByte = (byte) input.read();
			
			String firstBytes = String.format("%02x", firstByte) + String.format("%02x", secondByte);
			
			transactionBytesArray[i] = firstBytes;
			transactionBytesList.add(firstBytes);
		}
	}
	
	public Block getFullBlock(HashMap<Sha256Hash, Transaction> mempool, HashMap<String, LinkedList<Sha256Hash>> shortTxHashIndex) throws MissingTransactionException {
		
		ArrayList<Sha256Hash> mempoolTransactions = new ArrayList<Sha256Hash>();
		
		for (String firstBytes : transactionBytesArray) {
			if (shortTxHashIndex.containsKey(firstBytes)) {
				mempoolTransactions.addAll(shortTxHashIndex.get(firstBytes));				
			}
		}
		
		HashMap<Sha256Hash, Transaction> blockTxMap = new HashMap<Sha256Hash, Transaction>();
		List<Sha256Hash> sortedTransactions = new ArrayList<Sha256Hash>();
		
		sortedTransactions.add(reducedBlock.getTransactions().get(0).getHash());
		blockTxMap.put(reducedBlock.getTransactions().get(0).getHash(), reducedBlock.getTransactions().get(0));
		
		for (Sha256Hash txHash : mempoolTransactions) {
			if (transactionBloomFilter.mightContain(txHash.getBytes()) && !blockTxMap.containsKey(txHash)) {
				sortedTransactions.add(txHash);
				blockTxMap.put(txHash, mempool.get(txHash));
			}
		}
		
		Collections.sort(sortedTransactions);
		Transaction[] correctTxArray = new Transaction[transactionCount];
		correctTxArray[0] = reducedBlock.getTransactions().get(0);
		
		// Check transaction list
		sortedTransactions = removeFalsePositives(sortedTransactions, blockTxMap);
		
		for (int i=0; i<sortedTransactions.size(); i++) {
			short position = transactionPositionArray[i];
			Transaction tx = blockTxMap.get(sortedTransactions.get(i));
			correctTxArray[position] = tx;
		}
		
		List<Short> missingPosList = new LinkedList<Short>();
		
		for (int i=0; i<correctTxArray.length; i++) {
			if (correctTxArray[i] == null) {
				missingPosList.add((short) + i);
			}
		}
		
		if (missingPosList.size() > 0) {
			throw new MissingTransactionException(missingPosList);
		}
		
		List<Transaction> newTxList = Arrays.asList(correctTxArray);
		reducedBlock.replaceTransactionList(newTxList);
		
		reduced = false;
		
		return reducedBlock;
	}
	
	private int readInt(InputStream input) throws IOException {
		byte[] intAsBytes = new byte[Integer.BYTES];
		for (int i=0; i<Integer.BYTES; i++) {
			intAsBytes[i] = (byte) input.read();
		}
		
		ByteBuffer wrapped = ByteBuffer.wrap(intAsBytes);
		
		return wrapped.getInt();
	}
	
	private List<Sha256Hash> removeFalsePositives(List<Sha256Hash> sortedTransactions, HashMap<Sha256Hash, Transaction> blockTxMap) {
		boolean done = true;
		for (int i=0; i<sortedTransactions.size(); i++) {
			if (i < transactionBytesArray.length) {
				Transaction tx = blockTxMap.get(sortedTransactions.get(i));
				String firstBytes = transactionBytesArray[i];
				
				if (tx != null && tx.getHash() != null && !tx.getHashAsString().substring(0,4).equals(firstBytes) ) {
					boolean nextTxFound = false;
					for (int j=1; j<7; j++) {
						if (i+j >= sortedTransactions.size()) {
							break;
						}
						Transaction nextTx = blockTxMap.get(sortedTransactions.get(i+j));
						String nextHash = nextTx.getHashAsString();
						if (nextHash.startsWith(firstBytes)) {
							nextTxFound = true;
							break;
						}
					}
					if (nextTxFound) {
						sortedTransactions.remove(i);
					} else {
						sortedTransactions.add(i, null);
					}
					done = false;
					break;
				}
			}
		}
		
		if (done) {
			return sortedTransactions;
		} else {
			return 	removeFalsePositives(sortedTransactions, blockTxMap);		
		}
	}
	
	private short readShort(InputStream input) throws IOException {
		byte[] shortAsBytes = new byte[Short.BYTES];
		for (int i=0; i<Short.BYTES; i++) {
			shortAsBytes[i] = (byte) input.read();
		}
		
		ByteBuffer wrapped = ByteBuffer.wrap(shortAsBytes);
				
		return wrapped.getShort();
	}
	
	public byte[] serializeUthinBlock() throws IOException {
		
		ByteArrayOutputStream serializedBlock = new ByteArrayOutputStream();
		DataOutputStream dataOutputStream = new DataOutputStream(serializedBlock);
		
		dataOutputStream.write(blockHash.getBytes());
		dataOutputStream.write(merkleRoot.getBytes());
		int blockSize = reducedBlock.bitcoinSerialize().length;
		dataOutputStream.writeInt(blockSize);
		dataOutputStream.write(reducedBlock.bitcoinSerialize());
		
		ByteArrayOutputStream os = new ByteArrayOutputStream(); 
		transactionBloomFilter.writeTo(os);
		os.close();
		byte[] bfBytes = os.toByteArray();
		
		dataOutputStream.writeInt(bfBytes.length);
		dataOutputStream.write(bfBytes);
		
		dataOutputStream.writeInt(transactionPositionList.size());
		
		for (short position : transactionPositionList) {
			dataOutputStream.writeShort(position);
		}
		
		// Write the tx hash bytes
		dataOutputStream.writeInt(transactionBytesList.size());
		
		for (String bytesString : transactionBytesList) {
			dataOutputStream.write(DatatypeConverter.parseHexBinary(bytesString));
		}
		
		dataOutputStream.close();
		dataOutputStream.flush();
		
		return serializedBlock.toByteArray();
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append("Block hash: " + blockHash.toString());
		sb.append("Merkele root: " + merkleRoot.toString());
		
		sb.append("Block size: " + reducedBlock.bitcoinSerialize().length);
		sb.append("Tx position size: " + transactionPositionArray.length);
		
		return sb.toString();
	}
	
	public Sha256Hash getBlockHash() {
		return blockHash;
	}

	public Transaction getCoinbase() {
		return coinbase;
	}
	public void setCoinbase(Transaction coinbase) {
		this.coinbase = coinbase;
	}

}
