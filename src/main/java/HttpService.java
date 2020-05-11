import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import net.sf.json.JSONObject;
import wagu.Block;
import wagu.Board;
import wagu.Table;

public class HttpService {
	HttpURLConnection conn = null;
	final int BUFFER_SIZE = 4096;
	private static final int TIME_OUT = 100 * 1000; // 超時時間
	private static final String CHARSET = "utf-8"; // 編碼格式
	private static final String PREFIX = "--"; // 字首
	private static final String BOUNDARY = UUID.randomUUID().toString(); // 邊界標識 隨機生成
	private static final String CONTENT_TYPE = "multipart/form-data"; // 內容型別
	private static final String LINE_END = "\r\n"; // 換行

	public String runTestSuite(String address, Map<String, File> fileParams) throws IOException {
		URL url = new URL(address);
		conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("POST");
		conn.setReadTimeout(TIME_OUT);
		conn.setConnectTimeout(TIME_OUT);
		conn.setDoOutput(true);
		conn.setDoInput(true);
		conn.setUseCaches(false);// Post 請求不能使用快取
		// 設定請求頭引數
		conn.setRequestProperty("Connection", "Keep-Alive");
		conn.setRequestProperty("Charset", "UTF-8");
		conn.setRequestProperty("Content-Type", CONTENT_TYPE + ";boundary=" + BOUNDARY);

		DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
		StringBuilder fileSb = new StringBuilder();
		for (Map.Entry<String, File> fileEntry : fileParams.entrySet()) {
			fileSb.append(PREFIX).append(BOUNDARY).append(LINE_END)
					/**
					 * 這裡重點注意： name裡面的值為服務端需要的key 只有這個key 才可以得到對應的檔案 filename是檔案的名字，包含字尾名的
					 * 比如:abc.png
					 */
					.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileEntry.getKey() + "\""
							+ LINE_END)
//					.append("Content-Type: image/jpg" + LINE_END) // 此處的ContentType不同於 請求頭 中Content-Type
					.append("Content-Transfer-Encoding: 8bit" + LINE_END).append(LINE_END);// 引數頭設定完以後需要兩個換行，然後才是引數內容
			dos.writeBytes(fileSb.toString());
			dos.flush();
			InputStream is = new FileInputStream(fileEntry.getValue());
			byte[] buffer = new byte[1024];
			int len = 0;
			while ((len = is.read(buffer)) != -1) {
				dos.write(buffer, 0, len);
			}
			is.close();
			dos.writeBytes(LINE_END);
		}
		// 請求結束標誌
		dos.writeBytes(PREFIX + BOUNDARY + PREFIX + LINE_END);
		dos.flush();
		dos.close();

		StringBuilder response = new StringBuilder();
		// 讀取伺服器返回資訊
		if (conn.getResponseCode() == 200) {
			InputStream in = conn.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			String line = null;

			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
		}

		return response.toString();
	}

	public String getState(String address, final Map<String, String> params) throws IOException {
		StringBuilder response = new StringBuilder();
		HttpURLConnection conn = null;
		String dataParams = getDataString(params, 1);

//		System.out.println(address + dataParams);
		URL url = new URL(address + dataParams);
		conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");

		int responseCode = conn.getResponseCode();
		if (responseCode == HttpURLConnection.HTTP_OK) {
			String line;
			BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			while ((line = br.readLine()) != null) {
				response.append(line);
			}
		}

//		String resultState = JSONObject.fromObject(response.toString()).getString("state");

		return response.toString();
	}

	public String getTestCaseState(String address, final Map<String, String> params) throws IOException {
		StringBuilder response = new StringBuilder();
		HttpURLConnection conn = null;
		String dataParams = getDataString(params, 1);

		URL url = new URL(address + dataParams);
		conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");

		int responseCode = conn.getResponseCode();
		if (responseCode == HttpURLConnection.HTTP_OK) {
			String line;
			BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			while ((line = br.readLine()) != null) {
				response.append(line);
			}
		}

		String resultState = JSONObject.fromObject(response.toString()).getString("state");

		return resultState;
	}

//	public void downloadReport(String address, final Map<String, String> strParams, String folderPath) {
//		try {
//			final int BUFFER_SIZE = 4096;
//			URL url = new URL(address);
//			HttpURLConnection conn = null;
//			conn = (HttpURLConnection) url.openConnection();
//			conn.setRequestMethod("POST");
//			conn.setReadTimeout(TIME_OUT);
//			conn.setConnectTimeout(TIME_OUT);
//			conn.setDoOutput(true);
//			conn.setDoInput(true);
//			conn.setUseCaches(false);// Post 請求不能使用快取
//			// 設定請求頭引數
//			conn.setRequestProperty("Connection", "Keep-Alive");
//			conn.setRequestProperty("Charset", "UTF-8");
//			conn.setRequestProperty("Content-Type", CONTENT_TYPE + ";boundary=" + BOUNDARY);
//
//			OutputStream outputStream;
//			outputStream = conn.getOutputStream();
//			PrintWriter writer = new PrintWriter(outputStream, true);
//
//			writer.append(getStrParams(strParams).toString());
//			writer.append(PREFIX + BOUNDARY + PREFIX + LINE_END);
//			writer.flush();
//
//			int responseCode = conn.getResponseCode();
//
//			if (responseCode == HttpURLConnection.HTTP_OK) { // success
//				InputStream inputStream = conn.getInputStream();
//				// opens an output stream to save into file
//				FileOutputStream fileOutputStream = new FileOutputStream(folderPath + "/report.zip");// getReportPath()
//				int bytesRead = -1;
//				byte[] buffer = new byte[BUFFER_SIZE];
//				while ((bytesRead = inputStream.read(buffer)) != -1) {
//					fileOutputStream.write(buffer, 0, bytesRead);
//				}
//
//				fileOutputStream.close();
//				inputStream.close();
//				System.out.println("File downloaded");
//			} else {
//				System.out.println("POST request not worked");
//			}
//			conn.disconnect();
//		} catch (MalformedURLException e) {
//			e.printStackTrace();
//		} catch (UnsupportedEncodingException e) {
//			e.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}
	public void downloadReport(String address, final Map<String, String> strParams, String folderPath) {
		try {
			final int BUFFER_SIZE = 4096;
			HttpURLConnection conn = null;
			String dataParams = getDataString(strParams, 1);

			URL url = new URL(address + dataParams);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			
			int responseCode = conn.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) { // success
				InputStream inputStream = conn.getInputStream();
				
				// opens an output stream to save into file
				
				FileOutputStream fileOutputStream = new FileOutputStream(folderPath + "/reports.zip");// getReportPath()
				int bytesRead = -1;
				byte[] buffer = new byte[BUFFER_SIZE];
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					fileOutputStream.write(buffer, 0, bytesRead);
				}

				fileOutputStream.close();
				inputStream.close();
				System.out.println("File downloaded");
			} else {
				System.out.println("GET request not worked");
			}
			conn.disconnect();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static StringBuilder getStrParams(Map<String, String> params) {
		StringBuilder strSb = new StringBuilder();
		for (Map.Entry<String, String> entry : params.entrySet()) {
			strSb.append(PREFIX).append(BOUNDARY).append(LINE_END)
					.append("Content-Disposition: form-data; name=\"" + entry.getKey() + "\"" + LINE_END)
					.append("Content-Type: text/plain; charset=" + CHARSET + LINE_END)
					.append("Content-Transfer-Encoding: 8bit" + LINE_END).append(LINE_END)// 引數頭設定完以後需要兩個換行，然後才是引數內容
					.append(entry.getValue()).append(LINE_END);
		}
		return strSb;
	}

	String getDataString(Map<String, String> params, int methodType) throws UnsupportedEncodingException {
		final String UTF_8 = "UTF-8";
		StringBuilder result = new StringBuilder();
		boolean isFirst = true;
		for (Map.Entry<String, String> entry : params.entrySet()) {
			if (isFirst) {
				isFirst = false;
				if (methodType == 1) {
					result.append("?");
				}
			} else {
				result.append("&");
			}
			result.append(URLEncoder.encode(entry.getKey(), UTF_8));
			result.append("=");
			result.append(URLEncoder.encode(entry.getValue(), UTF_8));
		}
		return result.toString();
	}
	static TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // TODO Auto-generated method stub
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // TODO Auto-generated method stub
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            // TODO Auto-generated method stub
            return null;
        }
    } };
	
	public class NullHostNameVerifier implements HostnameVerifier {
        /*
         * (non-Javadoc)
         * 
         * @see javax.net.ssl.HostnameVerifier#verify(java.lang.String,
         * javax.net.ssl.SSLSession)
         */
		@Override
		public boolean verify(String arg0, SSLSession arg1) {
			// TODO Auto-generated method stub
			return true;
		}
    }

	public static void main(String[] args) throws IOException, KeyManagementException, NoSuchAlgorithmException{
		HttpsURLConnection.setDefaultHostnameVerifier(new HttpService().new NullHostNameVerifier());
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        
		HttpService httpService = new HttpService();
		Map<String, File> fileParams = new HashMap<String, File>();
		File file = new File("D:\\web\\Selab-web\\sideex-webservice-api\\inputs.zip");
		fileParams.put(file.getName(), file);
		
		
//		Map<String, String> params = new HashMap<String, String>();
//		params.put("token","2a76e50d-b47b-488b-a3dd-1dda3c84fb3c");
		try {
			System.out.println(httpService.runTestSuite("https://140.116.6.51:50000/sideex-webservice", fileParams));
//			System.out.println(httpService.getState("https://140.116.6.51:50000/sideex-webservice-state", params));
//			httpService.downloadReport("http://127.0.0.1:50000/sideex-webservice-reports", params, "D:\\Users\\kiam0\\Desktop\\test runner");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

//		File file = new File("D:\\web\\Selab-web\\sideex-webservice-api\\inputs.zip");
//	    if (file.exists() && file.isFile())
//	    {
//	      System.out.println("file exists, and it is a file");
//	    }

//		File tmpDir = new File("D:\\web\\Selab-web\\sideex-webservice-api\\");
//	    
//	    if (tmpDir.exists() && tmpDir.isDirectory()) {
//	    	System.out.println(tmpDir.getPath());
//	    	System.out.println("exists");
//	    } else {
//	    	System.out.println("no exists");
//	    }

//		try {
//			URL url = new URL("http://127.0.0.1:50000/sideex-webservice");
////			System.out.println(url.getProtocol()+"://"+url.getHost()+"/"+url.getPort());
//			System.out.println(url.toString());
//		} catch (MalformedURLException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}

		/* ======================== */
//		String arr[] = { "Untitled Test Suite", "Untitled Test Suite", "Untitled Test Suite", "Untitled Test Suite",
//				"Untitled Test Suite", "Untitled Test Suite", "Untitled Test Suite", "Untitled Test Suite",
//				"Untitled Test Suite", "Untitled Test Suite", "Untitled Test Suite", "Untitled Test Suite",
//				"Untitled Test Suite", "Untitled Test Suite", "Untitled Test Suite", "Untitled Test Suite" };
//
//		Board board = new Board(150);
//		Block summaryBlock = new Block(board, 148, 1, "Summary");
//		
//		Block suitesBlock = new Block(board, 15, 5, "Suites");
//		Block sideexVersionBlock = new Block(board, 15, 1, "SideexVersion");
//		Block browserBlock = new Block(board, 15, 1, "Browser");
//		Block platformRightBlock = new Block(board, 15, 1, "Platform");
//		Block languageBlock = new Block(board, 15, 1, "Language");
//		Block startTimeRightBlock = new Block(board, 15, 1, "StartTime");
//		Block endTimeBlock = new Block(board, 15, 1, "EndTime");
//		Block passedSuiteBlock = new Block(board, 15, 1, "PassedSuite");
//		Block passedCaseBlock = new Block(board, 15, 1, "PassedCase");
//
//		summaryBlock.setBelowBlock(suitesBlock.setDataAlign(Block.DATA_CENTER));
//		suitesBlock.setRightBlock(new Block(board, 132, 5, b(String.join(",", arr))).setDataAlign(Block.DATA_CENTER));
//		suitesBlock.setBelowBlock(sideexVersionBlock.setDataAlign(Block.DATA_CENTER));
//		sideexVersionBlock.setRightBlock(new Block(board, 132, 1, "3.3.4").setDataAlign(Block.DATA_CENTER));
//		sideexVersionBlock.setBelowBlock(browserBlock.setDataAlign(Block.DATA_CENTER));
//		browserBlock.setRightBlock(new Block(board, 132, 1, "chrome 80.0.3987.163").setDataAlign(Block.DATA_CENTER));
//		browserBlock.setBelowBlock(platformRightBlock.setDataAlign(Block.DATA_CENTER));
//		platformRightBlock.setRightBlock(new Block(board, 132, 1, "windows").setDataAlign(Block.DATA_CENTER));
//		platformRightBlock.setBelowBlock(languageBlock.setDataAlign(Block.DATA_CENTER));
//		languageBlock.setRightBlock(new Block(board, 132, 1, "(default)").setDataAlign(Block.DATA_CENTER));
//		languageBlock.setBelowBlock(startTimeRightBlock.setDataAlign(Block.DATA_CENTER));
//		startTimeRightBlock.setRightBlock(new Block(board, 132, 1, "20200410 04:45:02").setDataAlign(Block.DATA_CENTER));
//		startTimeRightBlock.setBelowBlock(endTimeBlock.setDataAlign(Block.DATA_CENTER));
//		endTimeBlock.setRightBlock(new Block(board, 132, 1, "20200410 04:45:05").setDataAlign(Block.DATA_CENTER));
//		endTimeBlock.setBelowBlock(passedSuiteBlock.setDataAlign(Block.DATA_CENTER));
//		passedSuiteBlock.setRightBlock(new Block(board, 132, 1, "0 / 1").setDataAlign(Block.DATA_CENTER));
//		passedSuiteBlock.setBelowBlock(passedCaseBlock.setDataAlign(Block.DATA_CENTER));
//		passedCaseBlock.setRightBlock(new Block(board, 132, 1, "0 / 1").setDataAlign(Block.DATA_CENTER));
//		
//		System.out.println(board.setInitialBlock(summaryBlock.setDataAlign(Block.DATA_CENTER)).build().getPreview());
	}

	public static String a(String ch, int n) {
		StringBuilder result = new StringBuilder(ch);
		for (int i = 0; i < n; i++) {
			result.append(ch);
		}
		return result.toString();
	}

	public static String b(String str) {
		StringBuilder result = new StringBuilder();
		StringBuilder temp = new StringBuilder();
		String arr[] = str.split(",");
		temp.append(arr[0]);
		for (int i = 1; i < arr.length; i++) {
//			System.out.println("i:"+i+" "+(temp.toString().length()+arr[i].length()));
			if ((temp.toString().length() + arr[i].length()) < 120) {
				temp.append("," + arr[i]);
			} else {
				result.append(temp.toString() + "\n");
				temp = new StringBuilder(arr[i]);
			}
		}
		result.append(temp.toString());
		return result.toString();
	}
}
