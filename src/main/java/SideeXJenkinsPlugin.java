import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import wagu.Block;
import wagu.Board;

public class SideeXJenkinsPlugin extends Builder {
	private String ipAddress;
	private String stateTime;
	private String inputsFilePath;
	private String reportFolderPath;

	@DataBoundConstructor
	public SideeXJenkinsPlugin(String ipAddress, String stateTime, String inputsFilePath, String reportFolderPath) {
		this.ipAddress = StringUtils.trim(ipAddress);
		this.stateTime = StringUtils.trim(stateTime);
		this.inputsFilePath = StringUtils.trim(inputsFilePath);
		this.reportFolderPath = StringUtils.trim(reportFolderPath);

		if (this.ipAddress.charAt(this.ipAddress.length() -1) != '/') {
			this.ipAddress = this.ipAddress+"/";
		}
		if (this.stateTime == "") {
			this.stateTime = "2000";
		}
	}

	@Override
	public boolean perform(Build<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {
        try {
        	HttpsURLConnection.setDefaultHostnameVerifier(new HttpService().new NullHostNameVerifier());
        	SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, trustAllCerts, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (KeyManagementException | NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		
		FilePath inputsFilePath = build.getProject().getSomeWorkspace().child(getInputsFilePath());
		FilePath reportFolderPath = build.getProject().getSomeWorkspace().child(getReportFolderPath());
		File inputsFile = new File(inputsFilePath.getRemote());
		File reportFolder = new File(reportFolderPath.getRemote());
		System.out.println("file path: "+reportFolder.toString());
		if (!(inputsFile.exists() && !inputsFile.isDirectory())) {
			listener.error("Specified test suites file path '" + inputsFilePath + "' does not exist.");
			build.setResult(Result.FAILURE);
			return true;
		}

		Map<String, File> fileParams = new HashMap<String, File>();
		Map<String, String> params;
		HttpService httpService = new HttpService();
		String token = "", resultToken = "", state = "", reportUrl = "", logUrl = "";
		JSONArray summary;
		boolean running = true, passed;

		fileParams.put(inputsFile.getName(), inputsFile);
		token = httpService.runTestSuite(getIpAddress() + "sideex-webservice", fileParams);// http://127.0.0.1:50000/

		if (!token.trim().equals("")) {
			resultToken = JSONObject.fromObject(token).getString("token");
			listener.getLogger().println(JSONObject.fromObject(token).toString());

			while (running) {
				params = new HashMap<String, String>();
				params.put("token", resultToken);
				state = httpService.getState(getIpAddress() + "sideex-webservice-state", params);

				listener.getLogger()
						.println("SideeX Runner state is " + JSONObject.fromObject(state).getString("state"));

				if (JSONObject.fromObject(state).getString("state").equals("finish")) {
					running = false;
					if (!getReportFolderPath().equals("")) {
						if (!reportFolder.exists()){
							reportFolder.mkdir();
					    }
						FileUtils.cleanDirectory(reportFolder);
						httpService.downloadReport(getIpAddress() + "sideex-webservice-reports", params,
								reportFolderPath.getRemote());
						String zipFilePath = reportFolderPath.getRemote() + "/reports.zip";
						String destDirectory = reportFolderPath.getRemote();
						UnzipUtility unzipper = new UnzipUtility();
						unzipper.unzip(zipFilePath, destDirectory);
						File reportsFile = new File(zipFilePath);
						
						reportsFile.delete();
					}

					passed = JSONObject.fromObject(state).getJSONObject("reports").getBoolean("passed");
					reportUrl = JSONObject.fromObject(state).getJSONObject("reports").getString("url").toString();
					logUrl = JSONObject.fromObject(state).getJSONObject("logs").getString("url").toString();
					summary = JSONObject.fromObject(state).getJSONObject("reports").getJSONArray("summarry");
					listener.getLogger().println("The test report can be download at " + reportUrl + ".");
					listener.getLogger().println("The log can be download at " + logUrl + ".");

					for (int i = 0; i < summary.size(); i++) {
						listener.getLogger().println(getSummarryFormat(JSONObject.fromObject(summary.get(i)), 60));
					}
					if (passed == false) {
						listener.error("Test Case Failed");
						build.setResult(Result.FAILURE);
					} else {
						listener.getLogger().println("Test Case Passed");
					}
				} else if (state.equals("fail")) {
					running = false;
					listener.error("Runner Failed");
					build.setResult(Result.FAILURE);
				} else {
					Thread.sleep(Long.valueOf(this.stateTime));
				}
			}
		}

		return true;
	}

	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return jobType == FreeStyleProject.class;
		}

		@Override
		public String getDisplayName() {
			return "Execute SideeX Web Testing";
		}

		public FormValidation doCheckIpAddress(@QueryParameter String ipAddress) {
			try {
				URL url = new URL(ipAddress);
				return FormValidation.ok();
			} catch (Exception e) {
				return FormValidation.error("Please enter a hostname");
			}
		}

		public FormValidation doCheckStateTime(@QueryParameter String stateTime) {
			try {
				Long.valueOf(stateTime);
				return FormValidation.ok();
			} catch (NumberFormatException e) {
				return FormValidation.error("Please enter a periodically time");
			}
		}
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

	public String getInputsFilePath() {
		return inputsFilePath;
	}

	public String getStateTime() {
		return stateTime;
	}

	public void setStateTime(String stateTime) {
		this.stateTime = stateTime;
	}

	public void setInputsFilePath(String inputsFilePath) {
		this.inputsFilePath = inputsFilePath;
	}

	public String getReportFolderPath() {
		return reportFolderPath;
	}

	public void setReportFolderPath(String reportFolderPath) {
		this.reportFolderPath = reportFolderPath;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

	public String getSummarryFormat(JSONObject object, int size) {
		final int leftRowWidth = 25;
		final int leftBlockWidth = 27;
		int maxWidth = 115;
		int suiteHight = 1;
		ArrayList<String> summarryList = summarryObjectToList(object);
		String suitesArray[] = splitLine(summarryList.get(0), maxWidth - leftBlockWidth - 1);
		String suites = String.join("\n", suitesArray);

		if (suitesArray.length == 1) {
			suiteHight = 1;
		} else {
			suiteHight = suitesArray.length + 2;
		}

		Board board = new Board(maxWidth);
		List<Integer> headerWidthLength = Arrays.asList(maxWidth - 2, 1);
		List<List<Integer>> colWidthLength = Arrays.asList(
				Arrays.asList(leftRowWidth, suiteHight, maxWidth - 28, suiteHight),
				Arrays.asList(leftRowWidth, 1, maxWidth - 28, 1), Arrays.asList(leftRowWidth, 1, maxWidth - 28, 1),
				Arrays.asList(leftRowWidth, 1, maxWidth - 28, 1), Arrays.asList(leftRowWidth, 1, maxWidth - 28, 1),
				Arrays.asList(leftRowWidth, 1, maxWidth - 28, 1), Arrays.asList(leftRowWidth, 1, maxWidth - 28, 1),
				Arrays.asList(leftRowWidth, 1, maxWidth - 28, 1), Arrays.asList(leftRowWidth, 1, maxWidth - 28, 1));
		List<List<String>> rowsList = Arrays.asList(Arrays.asList("Suites", suites),
				Arrays.asList("SideeX Version", summarryList.get(1)), Arrays.asList("Browser", summarryList.get(2)),
				Arrays.asList("Platform", summarryList.get(3)), Arrays.asList("Language", summarryList.get(4)),
				Arrays.asList("Start Time", summarryList.get(5)), Arrays.asList("End Time", summarryList.get(6)),
				Arrays.asList("Passed / Total Suite(s)", summarryList.get(7)),
				Arrays.asList("Passed / Total Case(s)", summarryList.get(8)));
		Block suitesBlock = listToBlock(board, rowsList, colWidthLength, "Report Summarry", headerWidthLength);

		return board.setInitialBlock(suitesBlock.setDataAlign(Block.DATA_CENTER)).build().getPreview();
	}

	public ArrayList<String> summarryObjectToList(JSONObject object) {
		DateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		String summarryTitle[] = { "Suites", "SideeXVersion", "Browser", "Platform", "Language", "StartTime", "EndTime",
				"PassedSuite", "TotalSuite", "PassedCase", "TotalPassedCase" };

		ArrayList<String> summarryList = new ArrayList<String>();
		summarryList.add(object.getJSONArray(summarryTitle[0]).toString().replaceAll("[\\[\\]\"]", ""));
		summarryList.add(object.getString(summarryTitle[1]).replaceAll("[\\[\\]]", "").replaceAll(",", "."));
		summarryList.add(object.getString(summarryTitle[2]));
		summarryList.add(object.getString(summarryTitle[3]));
		summarryList.add(object.getString(summarryTitle[4]));
		summarryList.add(sdf.format(object.getLong(summarryTitle[5])).replaceAll("/", ""));
		summarryList.add(sdf.format(object.getLong(summarryTitle[6])).replaceAll("/", ""));
		summarryList.add(object.getString(summarryTitle[7]) + " / " + object.getString(summarryTitle[8]));
		summarryList.add(object.getString(summarryTitle[9]) + " / " + object.getString(summarryTitle[10]));

		return summarryList;
	}

	public static Block listToBlock(Board board, List<List<String>> rowsList, List<List<Integer>> colWidthLength,
			String header, List<Integer> headerWidthLength) {
		List<Block> block = new ArrayList<>();

		for (int i = 0; i < rowsList.size(); i++) {
			List<String> row = rowsList.get(i);
			List<Integer> rowWidthLength = colWidthLength.get(i);
			if (block.size() == 0) {
				block.add(new Block(board, headerWidthLength.get(0), headerWidthLength.get(1), header)
						.setDataAlign(Block.DATA_CENTER));
				block.get(block.size() - 1)
						.setBelowBlock(new Block(board, rowWidthLength.get(0), rowWidthLength.get(1), row.get(0))
								.setDataAlign(Block.DATA_CENTER));
				block.add(block.get(block.size() - 1).getBelowBlock().setDataAlign(Block.DATA_CENTER));
				block.add(block.get(block.size() - 1)
						.setRightBlock(new Block(board, rowWidthLength.get(2), rowWidthLength.get(3), row.get(1))
								.setDataAlign(Block.DATA_CENTER)));
			} else {
				block.get(block.size() - 2)
						.setBelowBlock(new Block(board, rowWidthLength.get(0), rowWidthLength.get(1), row.get(0))
								.setDataAlign(Block.DATA_CENTER));
				block.add(block.get(block.size() - 2).getBelowBlock().setDataAlign(Block.DATA_CENTER));
				block.add(block.get(block.size() - 1)
						.setRightBlock(new Block(board, rowWidthLength.get(2), rowWidthLength.get(3), row.get(1))
								.setDataAlign(Block.DATA_CENTER)));
			}
		}

		return block.get(0);
	}

	public String[] splitLine(String str, int num) {
		ArrayList<String> result = new ArrayList<String>();
		StringBuilder temp = new StringBuilder();
		int index = 0;
		String arr[] = str.split(",");
		temp.append(arr[0]);
		for (int i = 1; i < arr.length; i++) {
			if ((temp.toString().length() + arr[i].length() + 2) < num) {
				temp.append(", " + arr[i]);
			} else {
				result.add(index++, temp.toString() + ",");
				temp = new StringBuilder(arr[i]);
			}
		}
		result.add(index, temp.toString());
		String[] array = new String[result.size()];
		System.arraycopy(result.toArray(), 0, array, 0, result.size());

		return array;
	}
}
