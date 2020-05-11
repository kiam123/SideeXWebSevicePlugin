import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

public class Main {

	public static void main(String[] args) {
//		String zipFilePath = "D:\\workspace\\SideeXPluginTest1\\work\\workspace\\abbb\\report.zip";
//        String destDirectory = "D:\\workspace\\SideeXPluginTest1\\work\\workspace\\abbb\\";
//        UnzipUtility unzipper = new UnzipUtility();
//        try {
//            unzipper.unzip(zipFilePath, destDirectory);
//        } catch (Exception ex) {
//            // some errors occurred
//            ex.printStackTrace();
//        }
		/***----------------***/
		File file = new File("D:\\Users\\kiam0\\Desktop\\asd\\New Text Document.zip");
		try {
			FileUtils.cleanDirectory(file);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		deleteDir(file);
	}

	public static void deleteDir(File dir) {
	    File[] files = dir.listFiles();
	    if(files != null) {
	        for (final File file : files) {
	            deleteDir(file);
	        }
	    }
	    dir.delete();
	}
}
