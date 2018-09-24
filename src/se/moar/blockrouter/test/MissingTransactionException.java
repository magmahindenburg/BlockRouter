package se.moar.blockrouter.test;

import java.util.LinkedList;
import java.util.List;

public class MissingTransactionException extends Exception {
	
	private List<Short> posList = new LinkedList<Short>();
	
	public MissingTransactionException(List<Short> posList) {
		super("Missing transaction from block");
		this.posList = posList;
	}
	
	public List<Short> getMissingTransactionPositions() {
		return posList;
	}

}
