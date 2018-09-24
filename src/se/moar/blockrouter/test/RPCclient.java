package se.moar.blockrouter.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.google.gson.Gson;

public class RPCclient {

	private static final String COMMAND_GET_BALANCE = "getbalance";
	private static final String COMMAND_GET_INFO = "getinfo";
	private static final String COMMAND_GET_NEW_ADDRESS = "getnewaddress";
	private static final String COMMAND_GET_BLOCKHASH = "getblockhash";
	private static final String COMMAND_GET_BLOCK = "getblock";
	private static final String COMMAND_GET_RAW_TRANSACTION = "getrawtransaction";
	private static final String COMMAND_SIGN_TRANSACTION = "signrawtransaction";
	private static final String COMMAND_SEND_RAW_TRANSACTION = "sendrawtransaction";
	private static final String COMMAND_SEND_FROM = "sendfrom";
	private static final String COMMAND_LIST_UNSPENT = "listunspent";
	private static final String COMMAND_GET_MEMPOOL = "getrawmempool";

	private JSONObject invokeRPC(String id, String method, List<Object> params) {
		DefaultHttpClient httpclient = new DefaultHttpClient();

		JSONObject json = new JSONObject();
		json.put("id", id);
		json.put("method", method);
		if (null != params) {
			JSONArray array = new JSONArray();
			array.addAll(params);
			json.put("params", params);
		}
		JSONObject responseJsonObj = null;
		try {
			String rpcHost = (String) ((Context) (new InitialContext().lookup("java:comp/env"))).lookup("rpchost");
			String rpcPort = (String) ((Context) (new InitialContext().lookup("java:comp/env"))).lookup("rpcport");
			String rpcUser = (String) ((Context) (new InitialContext().lookup("java:comp/env"))).lookup("rpcuser");
			String rpcPassword = (String) ((Context) (new InitialContext().lookup("java:comp/env"))).lookup("rpcpassword");
			
			httpclient.getCredentialsProvider().setCredentials(new AuthScope(rpcHost, Integer.valueOf(rpcPort)),
					new UsernamePasswordCredentials(rpcUser, rpcPassword));
			StringEntity myEntity = new StringEntity(json.toJSONString());
			HttpPost httppost = new HttpPost("http://"+ rpcHost +":" + rpcPort);
			httppost.setEntity(myEntity);

			HttpResponse response = httpclient.execute(httppost);
			HttpEntity entity = response.getEntity();

			JSONParser parser = new JSONParser();
			responseJsonObj = (JSONObject) parser.parse(EntityUtils.toString(entity));
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (org.json.simple.parser.ParseException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NamingException e) {
			e.printStackTrace();
		} finally {
			// When HttpClient instance is no longer needed,
			// shut down the connection manager to ensure
			// immediate deallocation of all system resources
			httpclient.getConnectionManager().shutdown();
		}
		return responseJsonObj;
	}

	public Double getBalance(String account) {
		String[] params = { account };
		JSONObject json = invokeRPC(UUID.randomUUID().toString(), COMMAND_GET_BALANCE, Arrays.asList(params));
		return (Double)json.get("result");
	}
	
	/*public List<UTXO> getUnspents() {
		return getUnspents(null);
	}
	
	public List<UTXO> getUnspents(String account) {
		List<UTXO> unspentList = new ArrayList<UTXO>();
		JSONObject json = invokeRPC(UUID.randomUUID().toString(), COMMAND_LIST_UNSPENT, null);
		Gson gson = new Gson();
		UTXO[] allUnspents = gson.fromJson(json.get("result").toString(), UTXO[].class);
		for (UTXO unspent : allUnspents) {
			if (account != null) {
				if (account.equals(unspent.getAccount())) {
					unspentList.add(unspent);
				}
			} else {
				unspentList.add(unspent);
			}
		}
		return unspentList;
	}*/

	public String getNewAddress(String account) {
		String[] params = { account };
		JSONObject json = invokeRPC(UUID.randomUUID().toString(), COMMAND_GET_NEW_ADDRESS, Arrays.asList(params));
		return (String)json.get("result");
	}
	
	public String sendFromHotwallet(String receiver, BigDecimal amount) {
		String[] params = { "hotwallet", receiver, amount.toPlainString() };
		JSONObject json = invokeRPC(UUID.randomUUID().toString(), COMMAND_SEND_FROM, Arrays.asList(params));
		return (String)json.get("result");
	}

	public JSONObject getInfo() {
		JSONObject json = invokeRPC(UUID.randomUUID().toString(), COMMAND_GET_INFO, null);
		return (JSONObject)json.get("result");
	}
	
	public JSONObject getBlock(String blockhash) {
		String[] params = { blockhash };
		JSONObject json = invokeRPC(UUID.randomUUID().toString(), COMMAND_GET_BLOCK, Arrays.asList(params));
		return (JSONObject)json.get("result");
	}
	
	public String getRawTx(String txhash) {
		String[] params = { txhash };
		JSONObject json = invokeRPC(UUID.randomUUID().toString(), COMMAND_GET_RAW_TRANSACTION, Arrays.asList(params));
		return (String)json.get("result");
	}
	
	public String getBlockhash(long blockheight) {
		Long[] params = { blockheight };
		JSONObject json = invokeRPC(UUID.randomUUID().toString(), COMMAND_GET_BLOCKHASH, Arrays.asList(params));
		return (String)json.get("result");
	}
	
	public List<Transaction> getRawMempool() {
		List<Transaction> txList = new ArrayList<Transaction>();
		JSONObject json = invokeRPC(UUID.randomUUID().toString(), COMMAND_GET_MEMPOOL, null);
		Gson gson = new Gson();
		String[] txHashes = gson.fromJson(json.get("result").toString(), String[].class);
		
		for (String txHash : txHashes) {
			String txHex = getRawTx(txHash);
			txList.add(new Transaction(MainNetParams.get(), HexStringHandler.hexStringToByteArray(txHex)));
		}
		
		return txList;
	}
	
	public String signTx(String rawTx) {
		String[] params = { rawTx };
		JSONObject json = invokeRPC(UUID.randomUUID().toString(), COMMAND_SIGN_TRANSACTION, Arrays.asList(params));
		JSONObject signedTx = (JSONObject) json.get("result");
		boolean complete = (boolean) signedTx.get("complete");
		
		if (complete) {
			return (String) signedTx.get("hex");
		}
		return null;
	}
	
	public String sendTx(String rawTx) {
		String[] params = { rawTx };
		JSONObject json = invokeRPC(UUID.randomUUID().toString(), COMMAND_SEND_RAW_TRANSACTION, Arrays.asList(params));
		return (String) json.get("result");
	}
	
	public String getBlockCoinbaseTx(String blockhash) {
		String[] params = { blockhash };
		JSONObject json = invokeRPC(UUID.randomUUID().toString(), COMMAND_GET_BLOCK, Arrays.asList(params));
		JSONObject block = (JSONObject) json.get("result");
		JSONArray txList = (JSONArray) block.get("tx");
		String txHash = (String) txList.get(0);
		return txHash;
	}
}