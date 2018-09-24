package se.moar.blockrouter.test;

import javax.xml.bind.DatatypeConverter;

public class RandomTester {

	public static void main(String[] args) {
		
		String bytesString = "e1b5";
		
		System.out.println(DatatypeConverter.parseHexBinary(bytesString)[1]);

	}

}
